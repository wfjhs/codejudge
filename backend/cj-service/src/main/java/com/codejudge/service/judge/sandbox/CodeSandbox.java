package com.codejudge.service.judge.sandbox;

import com.codejudge.model.judge.SandboxResult;
import com.codejudge.model.judge.SandboxStatus;

/**
 * 代码沙箱抽象：把"一段源码在受控环境里跑一次"这件事收敛成一个接口。
 * <p>
 * <b>为什么值得单独抽一层：</b>隔离级别的演进方向是可预期的——
 * 进程级（当前）→ 容器级（加 cgroup / seccomp / pids limit）。
 * 这两种实现的调用方式完全一样（编译、逐用例执行、取标准输出），
 * 差别全在"怎么把进程关进笼子"里。
 * <p>
 * 如果上层直接依赖 {@link LocalProcessSandbox} 这个具体类，
 * 将来换容器级实现时，{@code LocalJudgeEngine} 里所有调用点都要跟着改；
 * 依赖接口之后就只需要换一个 {@code new} 的类名。
 * <p>
 * <b>当前的隔离能力边界（不要高估）：</b>
 * 进程级沙箱靠 {@code ProcessBuilder} + 超时强杀 + {@code -Xmx} 限堆，
 * 拦不住 fork 炸弹、网络访问、越权读文件，也测不准内存峰值。
 * 这些必须靠容器 + cgroup 才能解决，属于已知边界而不是实现疏漏。
 */
public interface CodeSandbox extends AutoCloseable {

    /**
     * 编译阶段的结果。解释型语言（Python）没有编译步骤，直接返回 OK。
     */
    SandboxStatus getCompileStatus();

    /**
     * 编译失败时的诊断信息（已强制英文，避免跨平台编码问题）；成功时为 null。
     */
    String getCompileError();

    /**
     * 用指定输入执行一次，返回退出状态、标准输出、标准错误与耗时。
     *
     * @param input       标准输入
     * @param timeLimitMs 墙钟超时，超时后强杀进程
     */
    SandboxResult run(String input, long timeLimitMs);

    /**
     * 释放临时目录等资源。用 try-with-resources 调用，保证异常路径也会清理。
     */
    @Override
    void close();
}
