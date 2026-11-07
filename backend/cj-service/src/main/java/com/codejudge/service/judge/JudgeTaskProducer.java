package com.codejudge.service.judge;

import com.codejudge.model.judge.JudgeTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 判题任务生产者。
 * <p>
 * 只做一件事：把任务追加到 Redis Stream。失败时向上抛，由提交接口决定怎么处理
 * （当前策略是提交本身回滚，用户看到的是"提交失败，请重试"，而不是一个永远 PENDING 的记录）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskProducer {

    private final StringRedisTemplate redisTemplate;

    /**
     * 投递判题任务。
     * <p>
     * 用 XADD 的 MAXLEN ~ 参数在写入时顺带裁剪，避免 Stream 无限增长。
     *
     * @return 消息 ID（Redis 自动生成，形如 1730000000000-0）
     */
    public String submit(JudgeTask task) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(JudgeQueue.F_SUBMISSION_ID, String.valueOf(task.getSubmissionId()));
        body.put(JudgeQueue.F_PROBLEM_ID, String.valueOf(task.getProblemId()));
        body.put(JudgeQueue.F_USER_ID, String.valueOf(task.getUserId()));
        body.put(JudgeQueue.F_LANGUAGE, task.getLanguage());

        String recordId = redisTemplate.opsForStream()
                .add(StreamRecords.string(body).withStreamKey(JudgeQueue.STREAM_KEY))
                .getValue();

        log.debug("判题任务已投递：submissionId={}, recordId={}", task.getSubmissionId(), recordId);
        return recordId;
    }
}
