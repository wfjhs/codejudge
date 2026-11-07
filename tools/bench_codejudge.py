"""项目一（CodeJudge）压测脚本。

用法：
    python tools/bench_codejudge.py

前提：后端已在 localhost:8080 启动（MySQL / Redis 可用）。

输出两项指标：
1. [query]  题目详情查询（走 Cache-Aside 缓存）的 QPS / P50 / P99
2. [submit] 提交接口（异步落库 + 入队后立即返回）的 P50 / P99
"""

import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

BASE = "http://localhost:8080"
USERNAME = "benchuser"
PASSWORD = "bench123456"


def _request(path, method="GET", body=None, token=None, timeout=10):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read())


def login_or_register():
    try:
        _request("/api/auth/register", "POST",
                 {"username": USERNAME, "password": PASSWORD, "nickname": "bench"})
    except urllib.error.HTTPError:
        pass
    resp = _request("/api/auth/login", "POST", {"username": USERNAME, "password": PASSWORD})
    if resp.get("code") != 0:
        raise SystemExit("登录失败: %s" % resp)
    return resp["data"]["token"]


def bench_query(token, threads=16, per_thread=200):
    total = threads * per_thread

    def one(_):
        start = time.perf_counter()
        _request("/api/problems/1", token=token)
        return (time.perf_counter() - start) * 1000

    begin = time.perf_counter()
    with ThreadPoolExecutor(max_workers=threads) as pool:
        latencies = list(pool.map(one, range(total)))
    elapsed = time.perf_counter() - begin

    latencies.sort()
    qps = total / elapsed
    p50 = latencies[int(len(latencies) * 0.50)]
    p99 = latencies[min(len(latencies) - 1, int(len(latencies) * 0.99))]
    print("[query]  total=%d threads=%d elapsed=%.0fms QPS=%.0f P50=%.2fms P99=%.2fms max=%.2fms"
          % (total, threads, elapsed * 1000, qps, p50, p99, latencies[-1]))
    return qps, p50, p99


def bench_submit(token, count=20, interval=0.35):
    code = "public class Main { public static void main(String[] a){ System.out.println(1); } }"
    latencies = []
    ok = 0
    failed = 0
    for _ in range(count):
        body = {
            "problemId": 1,
            "language": "java",
            "code": code,
            "requestId": str(uuid.uuid4()),
        }
        start = time.perf_counter()
        try:
            resp = _request("/api/submissions", "POST", body, token)
            latencies.append((time.perf_counter() - start) * 1000)
            if resp.get("code") == 0:
                ok += 1
            else:
                failed += 1
        except Exception:
            failed += 1
        time.sleep(interval)

    latencies.sort()
    if latencies:
        print("[submit] ok=%d failed=%d P50=%.2fms P99=%.2fms max=%.2fms"
              % (ok, failed, latencies[len(latencies) // 2],
                 latencies[min(len(latencies) - 1, int(len(latencies) * 0.99))],
                 latencies[-1]))
    else:
        print("[submit] ok=0 failed=%d（全部失败，检查限流/鉴权）" % failed)


if __name__ == "__main__":
    token = login_or_register()
    print("token acquired")
    for _ in range(50):
        _request("/api/problems/1", token=token)
    bench_query(token)
    bench_submit(token)
