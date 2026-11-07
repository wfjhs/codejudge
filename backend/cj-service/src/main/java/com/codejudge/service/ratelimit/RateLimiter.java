package com.codejudge.service.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis + Lua 的令牌桶限流器。
 * <p>
 * 为什么用 Lua 而不是在 Java 里"先 GET 再 SET"：限流本质是"读余量 → 判断 → 扣减"三步，
 * 如果不是原子的，并发下会出现经典的竞态——两个请求同时读到"还有 1 个令牌"，都放行，
 * 实际却发了 2 个。Lua 脚本在 Redis 里是单线程执行的，把这三步打包成一条命令，
 * 原子性由 Redis 本身保证，不需要分布式锁。
 * <p>
 * 令牌桶相比固定窗口/滑动窗口的好处是<b>允许突发</b>：平时流量低时把令牌攒着，
 * 比赛开始那一瞬间能放过去一波（burst=capacity），而不是被固定窗口卡得请求全失败。
 * 这正是 OJ 的典型场景：考试/比赛开始瞬间提交洪峰，刚性限流会把正常用户也挡掉。
 * <p>
 * 性能：脚本里只有 O(1) 的 HMGET/HMSET，且 key 设置了 EXPIRE，空闲后自动回收，不会内存泄漏。
 */
@Slf4j
@Component
public class RateLimiter {

    /**
     * 令牌桶 Lua 脚本。
     * <pre>
     *   KEYS[1] = 限流键
     *   ARGV[1] = 速率（每秒补充的令牌数）
     *   ARGV[2] = 容量（桶大小，即最大突发）
     *   ARGV[3] = 当前毫秒时间戳
     *   ARGV[4] = 本次请求消耗的令牌数（通常 1）
     *   返回值：1 放行 / 0 拒绝
     * </pre>
     * 注意：只返回 allowed 标志（整数），令牌余量存在 hash 里（以字符串存，兼容小数），
     * 避免 Redis 把浮点令牌数截断成整数导致精度丢失。
     */
    private static final String TOKEN_BUCKET_LUA =
            "local rate = tonumber(ARGV[1])\n" +
            "local capacity = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local requested = tonumber(ARGV[4])\n" +
            "local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')\n" +
            "local tokens = tonumber(data[1])\n" +
            "local ts = tonumber(data[2])\n" +
            "if tokens == nil then\n" +
            "  tokens = capacity\n" +
            "  ts = now\n" +
            "end\n" +
            "local delta = (now - ts) / 1000 * rate\n" +
            "tokens = math.min(capacity, tokens + delta)\n" +
            "ts = now\n" +
            "local allowed = 0\n" +
            "if tokens >= requested then\n" +
            "  tokens = tokens - requested\n" +
            "  allowed = 1\n" +
            "end\n" +
            "redis.call('HMSET', KEYS[1], 'tokens', tostring(tokens), 'ts', ts)\n" +
            "redis.call('EXPIRE', KEYS[1], math.ceil(capacity / rate) + 2)\n" +
            "return allowed\n";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptText(TOKEN_BUCKET_LUA);
        s.setResultType(Long.class);
        this.script = s;
    }

    /**
     * 尝试获取令牌。
     *
     * @return true 放行，false 拒绝
     */
    public boolean tryAcquire(String key, int ratePerSec, int capacity) {
        long now = System.currentTimeMillis();
        try {
            Long allowed = redisTemplate.execute(script,
                    Collections.singletonList(key),
                    String.valueOf(ratePerSec), String.valueOf(capacity),
                    String.valueOf(now), "1");
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            // 限流器自身的故障（Redis 抖动等）绝不能雪崩到正常业务：兜底放行。
            // 代价是极端情况下限流失效，但比把整个判题系统拖挂强。
            log.warn("令牌桶限流执行失败，兜底放行：key={}, {}", key, e.getMessage());
            return true;
        }
    }
}
