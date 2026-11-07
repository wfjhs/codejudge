package com.codejudge.service.judge.sandbox;

import com.codejudge.model.enums.Language;
import com.codejudge.model.judge.SandboxResult;
import com.codejudge.model.judge.SandboxStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 进程级代码沙箱（无 Docker 时的降级实现）。
 * <p>
 * 这是 M4 的核心类。它把"用户提交的代码"放进一个<b>独立临时目录</b>，用 {@link ProcessBuilder}
 * 真正地编译（编译型语言）并运行，通过子进程完成资源隔离：
 * <ul>
 *     <li><b>时间隔离</b>：用 {@code Process.waitFor(timeout)} 等待，超时则 {@code destroyForcibly()} 强杀，
 *     避免死循环、sleep 炸弹把判题线程永远占住</li>
 *     <li><b>内存隔离（近似）</b>：Java 用 {@code -Xmx} 限制堆；进程级无法精确测峰值内存，
 *     真正的精确测量需要 cgroup / Docker，见类注释末尾</li>
 *     <li><b>文件系统隔离</b>：代码只写进自己的临时工作目录，跑完连目录一起删</li>
 *     <li><b>编译/运行解耦</b>：编译型语言（Java）编译一次，每个用例只跑，不重复编译</li>
 * </ul>
 * <p>
 * <b>诚实的边界</b>（面试被追问时直接讲，不丢人）：进程级隔离拦不住 fork 炸弹、拦不住用户代码
 * 主动发起的网络连接、拦不住读取机器上其他文件（取决于运行账户权限）。这些都是容器 + seccomp
 * + pids limit 才能彻底解决的。所以本类通过 {@code CodeSandbox} 接口暴露，等装了 Docker 之后
 * 只需再写一个 {@code DockerSandbox} 实现，上层 {@code LocalJudgeEngine} 一行都不用动。
 */
@Slf4j
public class LocalProcessSandbox implements CodeSandbox {

    private final Language language;
    private final long memoryLimitMb;
    private final String pythonCommand;
    private final Path workDir;

    /** 编译结果只算一次；编译型语言编译失败则后续所有用例直接返回 CE */
    private SandboxStatus compileStatus = SandboxStatus.OK;
    private String compileError;

    public LocalProcessSandbox(Language language, String code, long memoryLimitMb,
                               String pythonCommand, String workDirBase) throws IOException {
        this.language = language;
        this.memoryLimitMb = memoryLimitMb;
        this.pythonCommand = pythonCommand;
        // 必须先保证父目录存在：Files.createTempDirectory(dir, prefix) 要求 dir 已存在，
        // 否则首次判题会抛 NoSuchFileException（表现为 SE"沙箱执行异常"）
        Path baseDir = Paths.get(workDirBase);
        Files.createDirectories(baseDir);
        this.workDir = Files.createTempDirectory(baseDir, "sandbox-");
        writeSource(code);
        if (language.isCompiled()) {
            doCompile();
        }
    }

    public SandboxStatus getCompileStatus() {
        return compileStatus;
    }

    public String getCompileError() {
        return compileError;
    }

    // ==================== 编译 ====================

    private void writeSource(String code) throws IOException {
        Path src = workDir.resolve(language.getFileName());
        Files.writeString(src, code == null ? "" : code, StandardCharsets.UTF_8);
    }

    private void doCompile() {
        String javac = javaHomeBin("javac");
        Path outDir = workDir.resolve("out");
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            compileStatus = SandboxStatus.INTERNAL_ERROR;
            compileError = "无法创建编译输出目录：" + e.getMessage();
            return;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javac);
        // 源码文件是 UTF-8 写入的，必须显式告诉 javac，否则含中文注释/字符串的代码会误判 CE
        cmd.add("-encoding");
        cmd.add("UTF-8");
        // 强制 javac 用英文输出诊断信息。
        // 根因：javac 的 stderr 编码跟随平台默认（中文 Windows 是 GBK），
        // 而读取端统一按 UTF-8 解码，两边不一致就会得到 "????" 这样的乱码，
        // 用户看到的编译错误完全不可读。英文诊断是纯 ASCII，
        // 无论读取端用什么字符集都不会坏——这也是各家 OJ 的通行做法。
        cmd.add("-J-Duser.language=en");
        cmd.add("-J-Duser.country=US");
        cmd.add("-d");
        cmd.add(outDir.toString());
        // 关掉警告，避免 WARNING 刷屏污染 stderr 判断（CE 只看 exit code）
        cmd.add("-Xlint:none");
        cmd.add(workDir.resolve(language.getFileName()).toString());

        ProcessResult r = runProcess(cmd, "", 30_000L);
        if (r.exitCode != 0) {
            compileStatus = SandboxStatus.COMPILE_ERROR;
            compileError = r.stderr.isBlank() ? r.stdout : r.stderr;
        }
    }

    // ==================== 运行 ====================

    /**
     * 执行一次（一个测试用例的输入）。
     * <p>
     * 注意：返回时间含 JVM/解释器启动开销。精确判题应当只统计程序自身 CPU 时间，
     * 那需要在操作系统层面用 ptrace / cgroup 采集，本降级实现做不到，故时间限制留了余量，
     * 文档里如实标注。
     */
    public SandboxResult run(String input, long timeLimitMs) {
        if (compileStatus != SandboxStatus.OK) {
            SandboxResult r = new SandboxResult();
            r.setStatus(compileStatus);
            r.setStderr(compileError);
            return r;
        }

        List<String> cmd = buildRunCommand();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);

        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            return SandboxResult.error(SandboxStatus.INTERNAL_ERROR, "启动进程失败：" + e.getMessage());
        }

        long start = System.nanoTime();

        // 喂输入：写完后立刻关流，否则程序会一直阻塞在等待 stdin
        try (OutputStream os = p.getOutputStream()) {
            os.write(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 程序可能没读 stdin 就退出了，写会抛 BrokenPipe，忽略
        }

        // 两个流式读取线程，避免 stdout/stderr 任一被写满导致进程阻塞（经典死锁）
        StreamReader outReader = new StreamReader(p.getInputStream());
        StreamReader errReader = new StreamReader(p.getErrorStream());
        outReader.start();
        errReader.start();

        boolean finished;
        try {
            // 给 500ms 缓冲：让"刚好压线"的程序不被我们的时钟误杀
            finished = p.waitFor(timeLimitMs + 500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            finished = false;
        }

        if (!finished) {
            // 超时：强杀。destroyForcibly 发 SIGKILL，比 destroy 更彻底，避免残留子进程
            p.destroyForcibly();
            try {
                p.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SandboxResult r = new SandboxResult();
            r.setStatus(SandboxStatus.TIME_LIMIT);
            r.setTimeCostMs(timeLimitMs);
            return r;
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        try {
            outReader.join(1000);
            errReader.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitCode = p.exitValue();
        SandboxResult r = new SandboxResult();
        r.setStdout(outReader.getContent());
        r.setStderr(errReader.getContent());
        r.setTimeCostMs(elapsedMs);
        if (exitCode != 0) {
            // -XX:+ExitOnOutOfMemoryError 会在 OOM 时非零退出，这里统一归为运行时错误
            r.setStatus(SandboxStatus.RUNTIME_ERROR);
        } else {
            r.setStatus(SandboxStatus.OK);
        }
        return r;
    }

    private List<String> buildRunCommand() {
        List<String> cmd = new ArrayList<>();
        if (language == Language.JAVA) {
            cmd.add(javaHomeBin("java"));
            cmd.add("-cp");
            cmd.add(workDir.resolve("out").toString());
            // 统一子进程的标准输出编码为 UTF-8，与读取端保持一致。
            // 不指定的话，Windows 上用户程序打印中文会按 GBK 编码写出，
            // 而这边按 UTF-8 读回来，中文输出直接变成乱码。
            cmd.add("-Dfile.encoding=UTF-8");
            cmd.add("-Dstdout.encoding=UTF-8");
            cmd.add("-Dstderr.encoding=UTF-8");
            // 限制堆内存，防止用户申请超大数组把判题机拖垮
            cmd.add("-Xmx" + memoryLimitMb + "m");
            // OOM 时直接退出而不是抛异常让程序继续，exitCode 非零 → 判为 RE
            cmd.add("-XX:+ExitOnOutOfMemoryError");
            // 关掉 JIT 预热相关的后台线程噪音，对判题无实质影响，纯属让日志干净
            cmd.add("-XX:+UseSerialGC");
            cmd.add("Main");
        } else { // PYTHON
            cmd.add(pythonCommand);
            // 强制 Python 进入 UTF-8 模式：不然 Windows 下 stdin/stdout 走 GBK，
            // 中文输入会被解错，中文输出也会与读取端的 UTF-8 不一致
            cmd.add("-X");
            cmd.add("utf8");
            cmd.add("-u"); // 无缓冲，输出实时可见，避免管道缓冲导致 waitFor 卡住
            cmd.add(workDir.resolve(language.getFileName()).toString());
        }
        return cmd;
    }

    // ==================== 工具 ====================

    private static String javaHomeBin(String name) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String exe = os.contains("win") ? name + ".exe" : name;
        return Paths.get(System.getProperty("java.home"), "bin", exe).toString();
    }

    /**
     * 通用进程执行（用于编译阶段，无输入、一次性读全部输出）。
     */
    private ProcessResult runProcess(List<String> cmd, String input, long timeoutMs) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            try (OutputStream os = p.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
            StreamReader out = new StreamReader(p.getInputStream());
            StreamReader err = new StreamReader(p.getErrorStream());
            out.start();
            err.start();
            boolean finished = p.waitFor(timeoutMs + 1000, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new ProcessResult(-1, "", "编译超时");
            }
            out.join(1000);
            err.join(1000);
            return new ProcessResult(p.exitValue(), out.getContent(), err.getContent());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ProcessResult(-1, "", e.getMessage());
        }
    }

    private static final class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    /**
     * 后台线程：把 InputStream 全部读成字符串。
     * <p>
     * 用线程而不是循环 read 是必须的：主线程在 waitFor，如果不并行消费，
     * 子进程写满管道缓冲区就会阻塞，造成"程序明明在跑却判成卡死"的假象。
     */
    private static final class StreamReader extends Thread {
        private final InputStream in;
        private final StringBuilder sb = new StringBuilder();

        StreamReader(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[1024];
                int n;
                while ((n = br.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                }
            } catch (IOException ignored) {
            }
        }

        String getContent() {
            return sb.toString();
        }
    }

    /**
     * 删除临时工作目录（连目录带编译产物一起）。
     * <p>
     * 判题跑完必须清理，否则每次提交都会在临时盘留下一堆 Main.class。
     * 用 try-finally + 递归删除，删不干净也不影响下一次判题。
     */
    @Override
    public void close() {
        try {
            deleteRecursively(workDir);
        } catch (IOException e) {
            log.warn("清理沙箱工作目录失败（可忽略，系统会回收）：{}", workDir);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            // 先删文件，后删目录（Files.walk 默认是深序？这里是自顶向下，需要反转）
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
