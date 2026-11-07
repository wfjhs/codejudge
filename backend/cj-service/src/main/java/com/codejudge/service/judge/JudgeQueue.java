package com.codejudge.service.judge;

/**
 * 判题队列相关的常量。
 */
public final class JudgeQueue {

    private JudgeQueue() {
    }

    /** Stream 的 key */
    public static final String STREAM_KEY = "stream:judge";

    /** 消费者组名 */
    public static final String GROUP = "judge-group";

    /** 消息字段 */
    public static final String F_SUBMISSION_ID = "submissionId";
    public static final String F_PROBLEM_ID = "problemId";
    public static final String F_USER_ID = "userId";
    public static final String F_LANGUAGE = "language";

    /**
     * Stream 最大长度。
     * <p>
     * 用 XADD MAXLEN ~ 做近似修剪：判题任务处理完就没有留存价值，
     * 但完全不留又没法排查问题。保留最近 10000 条是"可观测性 + 内存"的折中。
     * 用 {@code ~} 而不是精确长度，是为了让 Redis 按整节点裁剪，避免每次 XADD 都做精确计算。
     */
    public static final long MAX_LEN = 10_000L;
}
