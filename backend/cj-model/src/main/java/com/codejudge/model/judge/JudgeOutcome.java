package com.codejudge.model.judge;

import com.codejudge.model.enums.SubmissionStatus;

/**
 * 判题结果。
 * <p>
 * 用 record 而不是普通类：这是一次性的不可变结果对象，判完之后只读不改，
 * record 天然满足"不可变 + 值语义"，也少写一堆 getter。
 *
 * @param status     终态（AC/WA/TLE/MLE/RE/CE/SE）
 * @param score      得分
 * @param timeCost   最大用例耗时（ms）
 * @param memoryCost 峰值内存（KB）
 * @param errorMsg   编译/运行错误信息，成功时为 null
 * @param failCase   第一个失败用例序号，从 1 开始；全通过时为 null
 */
public record JudgeOutcome(
        SubmissionStatus status,
        int score,
        Integer timeCost,
        Integer memoryCost,
        String errorMsg,
        Integer failCase
) {

    public static JudgeOutcome accepted(int score, int timeCost, int memoryCost) {
        return new JudgeOutcome(SubmissionStatus.AC, score, timeCost, memoryCost, null, null);
    }

    public static JudgeOutcome wrongAnswer(int score, int timeCost, int memoryCost, int failCase) {
        return new JudgeOutcome(SubmissionStatus.WA, score, timeCost, memoryCost, null, failCase);
    }

    /**
     * 带诊断信息的错误答案。
     * <p>
     * 只对<b>样例用例</b>生成 detail —— 隐藏用例的期望输出一旦回传给前端，
     * 用户就能直接抄答案，判题也就失去意义了。这条边界必须由调用方守住。
     *
     * @param detail 面向用户的诊断文本（期望输出 / 实际输出 / 常见原因提示）
     */
    public static JudgeOutcome wrongAnswer(int score, int timeCost, int memoryCost,
                                           int failCase, String detail) {
        return new JudgeOutcome(SubmissionStatus.WA, score, timeCost, memoryCost, detail, failCase);
    }

    public static JudgeOutcome of(SubmissionStatus status, String errorMsg, Integer failCase) {
        return new JudgeOutcome(status, 0, null, null, errorMsg, failCase);
    }
}
