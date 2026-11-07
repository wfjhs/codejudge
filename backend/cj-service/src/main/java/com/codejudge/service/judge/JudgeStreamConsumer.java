package com.codejudge.service.judge;

import com.codejudge.model.judge.JudgeTask;
import com.codejudge.service.SubmissionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 判题队列消费者（基于 Redis Stream 消费者组）。
 * <p>
 * 设计要点：
 * <ol>
 *     <li><b>消费者组</b>：多个消费者实例分摊消息，一条消息只投给组内一个消费者</li>
 *     <li><b>手动 ACK</b>：处理完才 XACK。进程崩溃时消息留在 PENDING 列表，不会丢</li>
 *     <li><b>故障转移</b>：定时用 XPENDING 找出超时未 ACK 的消息，XCLAIM 接管重试</li>
 *     <li><b>非阻塞轮询</b>：没有用 XREAD BLOCK，原因见 {@link #poll} 的注释</li>
 * </ol>
 * <p>
 * 语义是<b>至少一次（at-least-once）</b>而不是恰好一次：消息可能被重复投递，
 * 幂等由 {@code SubmissionServiceImpl#judge} 里的条件更新保证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "codejudge.judge.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class JudgeStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final SubmissionService submissionService;

    @Value("${codejudge.judge.consumer-name:judge-1}")
    private String consumerName;

    @Value("${codejudge.judge.concurrency:1}")
    private int concurrency;

    /** 无消息时的轮询间隔 */
    private static final long IDLE_SLEEP_MS = 200L;

    /** 多久没 ACK 算超时，可以被别的消费者认领 */
    private static final Duration CLAIM_MIN_IDLE = Duration.ofMinutes(5);

    /** 故障转移检查间隔 */
    private static final long RECLAIM_INTERVAL_MS = 60_000L;

    private volatile boolean running = true;

    private final List<Thread> workers = new ArrayList<>();

    @PostConstruct
    public void start() {
        ensureGroup();

        for (int i = 0; i < Math.max(1, concurrency); i++) {
            String name = consumerName + "-" + i;
            // JDK 21 虚拟线程：消费循环大部分时间在等 Redis IO，用虚拟线程几乎零成本
            Thread t = Thread.ofVirtual()
                    .name("judge-consumer-" + name)
                    .start(() -> loop(name));
            workers.add(t);
        }

        log.info("判题消费者已启动：stream={}, group={}, concurrency={}",
                JudgeQueue.STREAM_KEY, JudgeQueue.GROUP, workers.size());
    }

    @PreDestroy
    public void stop() {
        running = false;
        workers.forEach(Thread::interrupt);
        log.info("判题消费者已停止");
    }

    /**
     * 创建消费者组（已存在则忽略）。
     * <p>
     * <b>踩过的坑</b>：Redis 的 {@code XGROUP CREATE} 要求 Stream 必须已存在，
     * 而 Spring Data Redis 的 {@code createGroup} 不会自动带 {@code MKSTREAM}。
     * 首次启动时 Stream 还是空的，直接 createGroup 会失败，导致消费者组永远建不出来、
     * 判题消息永远没人消费。所以这里先用一条占位消息把 Stream 建出来，删掉占位消息后再建组。
     */
    private void ensureGroup() {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(JudgeQueue.STREAM_KEY))) {
                // 用占位消息创建空 Stream（XADD 会自动建 key），随后立即删除这条占位消息
                var initId = redisTemplate.opsForStream()
                        .add(org.springframework.data.redis.connection.stream.StreamRecords
                                .string(Map.of("__init__", "1"))
                                .withStreamKey(JudgeQueue.STREAM_KEY));
                if (initId != null) {
                    redisTemplate.opsForStream().delete(JudgeQueue.STREAM_KEY, initId);
                }
            }
            // ReadOffset.from("0") 表示从 Stream 开头消费：应用启动前积压的任务也要判
            redisTemplate.opsForStream().createGroup(
                    JudgeQueue.STREAM_KEY, ReadOffset.from("0"), JudgeQueue.GROUP);
            log.info("已创建消费者组：{}", JudgeQueue.GROUP);
        } catch (Exception e) {
            if (isBusyGroup(e)) {
                log.info("消费者组已存在：{}", JudgeQueue.GROUP);
            } else {
                // Redis 暂时不可用等异常不应该让整个应用起不来
                log.warn("创建消费者组失败（不影响启动，稍后会自动重试）：{}", e.toString());
            }
        }
    }

    /**
     * 判断异常链里是否包含 BUSYGROUP。
     * <p>
     * Spring 会把 Redis 的原始错误包成 "Error in execution"，BUSYGROUP 藏在下层 cause 的 message 里，
     * 只看最外层 getMessage() 判断不出来，必须逐层往下找。
     */
    private static boolean isBusyGroup(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String m = t.getMessage();
            if (m != null && m.contains("BUSYGROUP")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void loop(String name) {
        long lastReclaim = System.currentTimeMillis();

        while (running) {
            try {
                boolean hasData = poll(name, 10);

                // 故障转移：没活干的时候顺便检查有没有别的消费者"撂挑子"留下的消息
                if (System.currentTimeMillis() - lastReclaim > RECLAIM_INTERVAL_MS) {
                    reclaimStale(name);
                    lastReclaim = System.currentTimeMillis();
                }

                if (!hasData) {
                    // 不用 XREAD BLOCK 的原因：
                    // Lettuce 在共享连接上执行阻塞命令会阻塞该连接上的其他命令，
                    // 需要专门的连接池配置才能安全使用。短轮询实现简单、行为可预测，
                    // 200ms 的空转对 Redis 压力可以忽略（5 QPS）。
                    Thread.sleep(IDLE_SLEEP_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 自愈：启动时 Redis 不可用 / Stream 被清空过，都可能没有消费者组，
                // 这里发现 NOGROUP 就重建一次，避免消费者"空转"却永远不消费
                if (msg.contains("NOGROUP")) {
                    log.warn("消费者组不存在，尝试重建：{}", JudgeQueue.GROUP);
                    ensureGroup();
                }
                log.error("判题消费循环异常，5 秒后重试", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 拉一批新消息并处理。
     *
     * @return 是否拿到了数据（没有数据时调用方会 sleep）
     */
    private boolean poll(String name, int count) {
        StreamOperations<String, String, String> ops = redisTemplate.opsForStream();

        List<MapRecord<String, String, String>> records = ops.read(
                Consumer.from(JudgeQueue.GROUP, name),
                StreamReadOptions.empty().count(count),
                // lastConsumed() 即 ">"：只读从未投递给这个组的新消息
                StreamOffset.create(JudgeQueue.STREAM_KEY, ReadOffset.lastConsumed()));

        if (records == null || records.isEmpty()) {
            return false;
        }

        for (MapRecord<String, String, String> record : records) {
            handle(name, record.getId().getValue(), record.getValue());
        }
        return true;
    }

    private void handle(String name, String recordId, Map<String, String> body) {
        try {
            JudgeTask task = toTask(body);
            submissionService.judge(task);
            // 处理完成才 ACK：中途崩溃的话消息会留在 PENDING 列表，等 reclaimStale 接管
            redisTemplate.opsForStream().acknowledge(JudgeQueue.STREAM_KEY, JudgeQueue.GROUP, recordId);
        } catch (Exception e) {
            // 不 ACK：让消息留在 PENDING，5 分钟后由故障转移重新认领
            log.error("处理判题消息失败，保留待重试：consumer={}, recordId={}, body={}",
                    name, recordId, body, e);
        }
    }

    /**
     * 故障转移：认领超过 {@link #CLAIM_MIN_IDLE} 未被 ACK 的消息。
     * <p>
     * 典型场景是判题进程被 kill -9，消息已经投递但没处理完，
     * 不做这一步的话这些提交会永远卡在 PENDING/JUDGING。
     */
    private void reclaimStale(String name) {
        try {
            StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
            PendingMessages pending = ops.pending(
                    JudgeQueue.STREAM_KEY, JudgeQueue.GROUP,
                    org.springframework.data.domain.Range.unbounded(), 50);

            if (pending == null || pending.isEmpty()) {
                return;
            }

            List<org.springframework.data.redis.connection.stream.RecordId> stale = new ArrayList<>();
            for (PendingMessage pm : pending) {
                if (pm.getElapsedTimeSinceLastDelivery().compareTo(CLAIM_MIN_IDLE) > 0) {
                    stale.add(pm.getId());
                }
            }

            if (stale.isEmpty()) {
                return;
            }

            // XCLAIM 只负责把消息归属改到当前消费者，它的返回值是 ByteRecord（byte[] 键值对），
            // 还要手动转码，不划算。更清晰的做法是：认领完再用 "0" 重新读一遍自己的 pending 列表。
            ops.claim(JudgeQueue.STREAM_KEY, JudgeQueue.GROUP, name,
                    CLAIM_MIN_IDLE, stale.toArray(
                            new org.springframework.data.redis.connection.stream.RecordId[0]));

            log.warn("故障转移：认领 {} 条超时未确认的判题消息（consumer={}）", stale.size(), name);

            // ReadOffset.from("0") 表示读取"本消费者已投递但未 ACK"的消息，
            // 与 poll() 里的 lastConsumed()(">") 不同：后者只读全新消息
            List<MapRecord<String, String, String>> reclaimed = ops.read(
                    Consumer.from(JudgeQueue.GROUP, name),
                    StreamReadOptions.empty().count(50),
                    StreamOffset.create(JudgeQueue.STREAM_KEY, ReadOffset.from("0")));

            if (reclaimed != null) {
                for (MapRecord<String, String, String> record : reclaimed) {
                    handle(name, record.getId().getValue(), record.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("故障转移检查失败：{}", e.getMessage());
        }
    }

    private JudgeTask toTask(Map<String, String> body) {
        JudgeTask task = new JudgeTask();
        String sid = body.get(JudgeQueue.F_SUBMISSION_ID);
        String pid = body.get(JudgeQueue.F_PROBLEM_ID);
        String uid = body.get(JudgeQueue.F_USER_ID);
        task.setSubmissionId(sid == null ? null : Long.valueOf(sid));
        task.setProblemId(pid == null ? null : Long.valueOf(pid));
        task.setUserId(uid == null ? null : Long.valueOf(uid));
        task.setLanguage(body.get(JudgeQueue.F_LANGUAGE));
        return task;
    }
}
