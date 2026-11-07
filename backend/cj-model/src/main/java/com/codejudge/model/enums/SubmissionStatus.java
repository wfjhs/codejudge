package com.codejudge.model.enums;

/**
 * 提交状态。
 * <p>
 * 状态流转是单向的：
 * <pre>
 * PENDING ──> JUDGING ──> AC / WA / TLE / MLE / RE / CE / SE
 * </pre>
 * 只有 PENDING 能被抢占为 JUDGING（靠数据库条件更新保证），
 * 这是判题幂等的核心。
 */
public enum SubmissionStatus {

    /** 已入库，等待判题 */
    PENDING,

    /** 判题机已抢占，正在判题 */
    JUDGING,

    /** 通过 */
    AC,

    /** 答案错误 */
    WA,

    /** 超时 */
    TLE,

    /** 内存超限 */
    MLE,

    /** 运行时错误 */
    RE,

    /** 编译错误 */
    CE,

    /** 系统错误（判题机自身异常） */
    SE;

    /**
     * 是否已经是终态。终态不会被再次判题。
     */
    public boolean isFinished() {
        return this != PENDING && this != JUDGING;
    }

    /**
     * 是否算作"通过"。
     */
    public boolean isAccepted() {
        return this == AC;
    }
}
