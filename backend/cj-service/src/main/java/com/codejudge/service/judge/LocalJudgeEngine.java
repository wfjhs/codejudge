package com.codejudge.service.judge;

import com.codejudge.model.entity.Problem;
import com.codejudge.model.entity.Submission;
import com.codejudge.model.entity.TestCase;
import com.codejudge.model.enums.Language;
import com.codejudge.model.enums.SubmissionStatus;
import com.codejudge.model.judge.JudgeOutcome;
import com.codejudge.model.judge.SandboxResult;
import com.codejudge.model.judge.SandboxStatus;
import com.codejudge.service.judge.sandbox.CodeSandbox;
import com.codejudge.service.judge.sandbox.LocalProcessSandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 真实判题引擎（进程级沙箱实现）。
 * <p>
 * 逻辑非常朴素，但每一条都有讲究：
 * <ol>
 *     <li>编译只做一次（编译型语言），编译错直接判 CE，不浪费后续运行</li>
 *     <li>逐用例执行，任意一个 TLE/RE 立即终止并返回（不用跑完所有用例）</li>
 *     <li>输出比对做归一化（去尾部空白、统一换行符），否则用户多打一个空格就被判 WA，太严</li>
 *     <li>得分 = 通过的用例得分之和；失败用例序号记第一个，方便用户定位</li>
 * </ol>
 * <p>
 * 通过 {@code codejudge.judge.engine=local} 激活。把它和 {@link MockJudgeEngine} 用 {@code @ConditionalOnProperty}
 * 互斥，spring 启动时只会实例化一个，永远不会有"两个引擎打架"的问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "codejudge.judge.engine", havingValue = "local")
public class LocalJudgeEngine implements JudgeEngine {

    @Value("${codejudge.judge.sandbox.python-command:python}")
    private String pythonCommand;

    @Value("${codejudge.judge.sandbox.work-dir-base:${java.io.tmpdir}/codejudge-sandbox}")
    private String workDirBase;

    @Override
    public JudgeOutcome judge(Submission submission, Problem problem, List<TestCase> testCases) {
        Language language = Language.of(submission.getLanguage());
        if (language == null) {
            return JudgeOutcome.of(SubmissionStatus.SE, "不支持的编程语言：" + submission.getLanguage(), null);
        }

        long timeLimit = problem.getTimeLimit() == null ? 2000 : problem.getTimeLimit();
        long memoryLimit = problem.getMemoryLimit() == null ? 256 : problem.getMemoryLimit();

        // 变量声明为 CodeSandbox 接口而不是具体类：
        // 将来换成容器级实现，这一行之外都不用动
        try (CodeSandbox sandbox = new LocalProcessSandbox(
                language, submission.getCode(), memoryLimit, pythonCommand, workDirBase)) {

            // ---- 编译阶段（仅编译型语言）----
            if (sandbox.getCompileStatus() == SandboxStatus.COMPILE_ERROR) {
                return JudgeOutcome.of(SubmissionStatus.CE, sandbox.getCompileError(), null);
            }

            // ---- 逐用例执行 ----
            int totalScore = testCases.stream()
                    .mapToInt(tc -> tc.getScore() == null ? 0 : tc.getScore())
                    .sum();
            int passedScore = 0;
            long maxTime = 0;
            long maxMem = 0;

            for (int i = 0; i < testCases.size(); i++) {
                TestCase tc = testCases.get(i);
                SandboxResult res = sandbox.run(tc.getInput(), timeLimit);
                maxTime = Math.max(maxTime, res.getTimeCostMs());
                maxMem = Math.max(maxMem, res.getMemoryCostKb());

                if (res.getStatus() == SandboxStatus.TIME_LIMIT) {
                    return JudgeOutcome.of(SubmissionStatus.TLE,
                            "执行超时（限制 " + timeLimit + "ms）", null);
                }
                if (res.getStatus() == SandboxStatus.RUNTIME_ERROR) {
                    String msg = res.getStderr();
                    if (msg != null && msg.length() > 1500) {
                        msg = msg.substring(0, 1500) + "...(truncated)";
                    }
                    return JudgeOutcome.of(SubmissionStatus.RE,
                            msg == null || msg.isBlank() ? "运行时错误（非零退出）" : msg, null);
                }
                if (res.getStatus() == SandboxStatus.INTERNAL_ERROR) {
                    return JudgeOutcome.of(SubmissionStatus.SE, res.getStderr(), null);
                }

                // 输出比对：归一化后全等才算过
                if (!isAccepted(res.getStdout(), tc.getOutput())) {
                    int failCase = i + 1;
                    // 第一个失败用例即终止，返回已通过的分数，并附带可读的诊断信息
                    return JudgeOutcome.wrongAnswer(passedScore, (int) maxTime, (int) maxMem, failCase,
                            describeWrongAnswer(failCase, tc, res.getStdout()));
                }
                passedScore += (tc.getScore() == null ? 0 : tc.getScore());
            }

            // 全部通过
            return JudgeOutcome.accepted(totalScore, (int) maxTime, (int) maxMem);

        } catch (IOException e) {
            log.error("沙箱初始化/清理失败：submissionId={}", submission.getId(), e);
            return JudgeOutcome.of(SubmissionStatus.SE, "沙箱执行异常：" + e.getMessage(), null);
        }
    }

    /**
     * 生成 WA 的诊断文本。
     * <p>
     * <b>只对样例用例展示内容</b>：隐藏用例的期望输出一旦下发到前端，
     * 用户直接抄答案就行，判题就形同虚设。隐藏用例只给序号。
     * <p>
     * "实际输出为空"是新手最常见的失败原因（算完了但忘了 print / println），
     * 单独给一句提示，比让人对着"第 1 个用例失败"猜半天有用得多。
     */
    private String describeWrongAnswer(int failCase, TestCase tc, String actual) {
        boolean sample = tc.getIsSample() != null && tc.getIsSample() == 1;
        if (!sample) {
            return "第 " + failCase + " 个用例未通过（隐藏用例，不展示具体内容）";
        }
        String out = "第 " + failCase + " 个用例未通过\n"
                + "期望输出：\n" + preview(tc.getOutput()) + "\n"
                + "实际输出：\n" + preview(actual);
        if (actual == null || actual.isBlank()) {
            out += "\n提示：程序没有任何标准输出，检查是否遗漏了 print / println 之类的输出语句";
        }
        return out;
    }

    /** 超长输出截断，避免把 error_msg 撑爆（DB 字段虽然能存，但前端也展示不下） */
    private String preview(String s) {
        if (s == null || s.isBlank()) {
            return "（空）";
        }
        String t = s.strip();
        return t.length() > 300 ? t.substring(0, 300) + "\n...(已截断)" : t;
    }

    /**
     * 输出比对。
     * <p>
     * 归一化规则：统一换行符为 \n，去掉整体首尾空白。这样用户多一个空格、多一行空行、
     * 用 \r\n 还是 \n 都不会影响判定——这些都不是算法正确性的体现，判 WA 只会让用户觉得系统有病。
     * 但注意：<b>不会</b>做"按行 trim"，因为有些题输出本身需要行内前导空格（如格式化图形）。
     */
    private boolean isAccepted(String actual, String expected) {
        if (expected == null) {
            return actual == null || actual.isBlank();
        }
        String a = normalize(actual);
        String e = normalize(expected);
        return a.equals(e);
    }

    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace("\r", "\n").strip();
    }
}
