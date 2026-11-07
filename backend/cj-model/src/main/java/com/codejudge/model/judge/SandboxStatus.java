package com.codejudge.model.judge;

/**
 * 沙箱单次执行的退出状态。
 * <p>
 * 与 {@link SubmissionStatus} 不同：这里是"一次程序运行"的结果，粒度更细。
 * 比如 CE（编译错误）只发生在编译型语言，而判题终态里的 WA/TLE 是"跑完所有用例后"得出的。
 */
public enum SandboxStatus {

    /** 正常结束，退出码 0 */
    OK,

    /** 编译失败 */
    COMPILE_ERROR,

    /** 运行时错误（崩溃 / 非零退出 / OOM） */
    RUNTIME_ERROR,

    /** 超过时间限制（被强制杀死） */
    TIME_LIMIT,

    /** 沙箱自身异常（不应发生，出现即兜底） */
    INTERNAL_ERROR
}
