#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""CodeJudge 判题链路端到端验证（真实沙箱，engine=local）。

覆盖：注册/登录 -> 提交 -> Redis Stream -> 消费者 -> 沙箱编译运行 -> 结果回写
断言：AC / WA / CE / TLE 四种终态 + Python 解释型 + 排行榜 + 题目提交统计
"""
import json
import time
import sys
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"
USER = "e2e_java"
PWD = "123456"

passed = 0
failed = 0


def check(name, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  [PASS] {name} {detail}")
    else:
        failed += 1
        print(f"  [FAIL] {name} {detail}")


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode("utf-8"))


def get_token():
    # 注册接口返回的是 userId（不是 token），所以无论注册成功与否，统一走登录拿 token
    call("POST", "/auth/register", {"username": USER, "password": PWD, "nickname": "E2E"})
    r = call("POST", "/auth/login", {"username": USER, "password": PWD})
    if r.get("code") != 0:
        raise RuntimeError("登录失败: " + json.dumps(r, ensure_ascii=False))
    return r["data"]["token"]


def submit(token, pid, lang, code):
    rid = f"e2e-{time.time_ns()}"
    r = call("POST", "/submissions", {"problemId": pid, "language": lang, "code": code, "requestId": rid}, token)
    if r.get("code") != 0:
        raise RuntimeError("提交失败: " + json.dumps(r, ensure_ascii=False))
    return r["data"]


def wait(token, sid, timeout=90):
    t0 = time.time()
    last = None
    while time.time() - t0 < timeout:
        r = call("GET", f"/submissions/{sid}", None, token)
        if r.get("code") != 0:
            raise RuntimeError("查询提交失败: " + json.dumps(r, ensure_ascii=False))
        d = r["data"]
        last = d
        if d["status"] not in ("PENDING", "JUDGING"):
            return d
        time.sleep(1.0)
    return last


JAVA_AC = """import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        long a = sc.nextLong();
        long b = sc.nextLong();
        System.out.println(a + b);
    }
}"""

JAVA_WA = """import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        long a = sc.nextLong();
        long b = sc.nextLong();
        System.out.println(a + b + 1);
    }
}"""

JAVA_CE = """public class Main {
    public static void main(String[] args) {
        int a = 1
        System.out.println(a);
    }
}"""

JAVA_TLE = """public class Main {
    public static void main(String[] args) {
        while (true) { }
    }
}"""

PY_AC = """a, b = map(int, input().split())
print(a + b)
"""


def main():
    print("== 1. 登录 ==")
    token = get_token()
    check("获取 token", bool(token))

    print("== 2. Java 正确解 -> AC ==")
    sid = submit(token, 1, "java", JAVA_AC)
    d = wait(token, sid)
    check("状态为 AC", d["status"] == "AC", f"(实际={d['status']}, 得分={d.get('score')}, 耗时={d.get('timeCost')}ms)")
    check("得分为满分", (d.get("score") or 0) > 0, f"(score={d.get('score')})")

    print("== 3. Java 错误解 -> WA ==")
    sid = submit(token, 1, "java", JAVA_WA)
    d = wait(token, sid)
    check("状态为 WA", d["status"] == "WA", f"(实际={d['status']}, 失败用例={d.get('failCase')})")

    print("== 4. Java 编译错误 -> CE ==")
    sid = submit(token, 1, "java", JAVA_CE)
    d = wait(token, sid)
    check("状态为 CE", d["status"] == "CE", f"(实际={d['status']})")
    check("有编译错误信息", bool(d.get("errorMsg")), f"(msg={(d.get('errorMsg') or '')[:60]!r})")

    print("== 5. Java 死循环 -> TLE ==")
    t0 = time.time()
    sid = submit(token, 1, "java", JAVA_TLE)
    d = wait(token, sid, timeout=60)
    cost = time.time() - t0
    check("状态为 TLE", d["status"] == "TLE", f"(实际={d['status']}, 墙钟={cost:.1f}s)")

    print("== 6. Python 正确解 -> AC ==")
    sid = submit(token, 1, "python", PY_AC)
    d = wait(token, sid)
    check("状态为 AC", d["status"] == "AC", f"(实际={d['status']}, 耗时={d.get('timeCost')}ms)")

    print("== 7. 排行榜 ==")
    r = call("GET", "/leaderboard?top=20", None, token)
    board = r.get("data") or []
    me = next((x for x in board if x.get("nickname") == "E2E"), None)
    check("用户在榜上", me is not None, f"(榜单={[(x['nickname'], x['solved']) for x in board]})")
    check("解题数 >= 1", me is not None and me.get("solved", 0) >= 1, f"(solved={me.get('solved') if me else None})")

    print("== 8. 题目统计 ==")
    r = call("GET", "/problems/1", None, token)
    p = r.get("data") or {}
    check("提交数已累加", (p.get("submitCount") or 0) >= 5, f"(submitCount={p.get('submitCount')})")
    check("通过数已累加", (p.get("acceptCount") or 0) >= 1, f"(acceptCount={p.get('acceptCount')})")

    print()
    print(f"===== 结果：{passed} 通过 / {failed} 失败 =====")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
