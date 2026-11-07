package com.codejudge.model.judge;

import lombok.Data;

/**
 * 沙箱单次执行的返回。
 * <p>
 * 一次判题可能有 N 个用例，每个用例对应一次 {@code SandboxResult}。
 * 内存字段在进程级隔离下无法精确测量（需要 cgroup / Docker），
 * 因此这里只尽量回填，拿不到时保持 0，真实精确值留待容器化实现。
 */
@Data
public class SandboxResult {

    private SandboxStatus status;

    private String stdout;

    private String stderr;

    /** 本次运行耗时（ms），含进程启动开销 */
    private long timeCostMs;

    /** 本次运行峰值内存（KB），拿不到时为 0 */
    private long memoryCostKb;

    public static SandboxResult error(SandboxStatus status, String message) {
        SandboxResult r = new SandboxResult();
        r.setStatus(status);
        r.setStderr(message);
        return r;
    }
}
