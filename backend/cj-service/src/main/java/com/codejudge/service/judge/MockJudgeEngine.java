package com.codejudge.service.judge;

import com.codejudge.model.entity.Problem;
import com.codejudge.model.entity.Submission;
import com.codejudge.model.entity.TestCase;
import com.codejudge.model.enums.SubmissionStatus;
import com.codejudge.model.judge.JudgeOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 判题引擎的 Mock 实现（M3 阶段使用）。
 * <p>
 * <b>它不执行任何用户代码</b>，只根据源码里的标记返回对应状态，
 * 目的是让"提交 → 队列 → 消费 → 回写"这条链路先跑通，
 * 并且能在测试里稳定地造出每一种判题结果。
 * <p>
 * 通过源码中的关键字控制返回值：
 * <ul>
 *     <li>{@code FORCE_CE} → 编译错误</li>
 *     <li>{@code FORCE_WA} → 答案错误</li>
 *     <li>{@code FORCE_TLE} → 超时</li>
 *     <li>{@code FORCE_RE}  → 运行时错误</li>
 *     <li>其他            → 通过</li>
 * </ul>
 * <p>
 * M4 阶段会换成真正执行代码的沙箱实现，届时通过配置
 * {@code codejudge.judge.engine=local} 切换，本类自动失效。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "codejudge.judge.engine", havingValue = "mock", matchIfMissing = true)
public class MockJudgeEngine implements JudgeEngine {

    @Override
    public JudgeOutcome judge(Submission submission, Problem problem, List<TestCase> testCases) {
        // 模拟判题耗时，让"排队中"的状态在界面上是可见的，
        // 否则本地测试时永远是瞬间完成，看不出异步的效果
        simulateCost();

        String code = submission.getCode() == null ? "" : submission.getCode();
        int totalScore = testCases.stream()
                .mapToInt(tc -> tc.getScore() == null ? 0 : tc.getScore())
                .sum();

        if (code.contains("FORCE_CE")) {
            return JudgeOutcome.of(SubmissionStatus.CE,
                    "Main.java:3: error: ';' expected\n    int a = 1\n             ^\n1 error", null);
        }
        if (code.contains("FORCE_TLE")) {
            return new JudgeOutcome(SubmissionStatus.TLE, 0,
                    problem.getTimeLimit(), 2048,
                    "执行超时（限制 " + problem.getTimeLimit() + "ms）", 2);
        }
        if (code.contains("FORCE_RE")) {
            return JudgeOutcome.of(SubmissionStatus.RE,
                    "Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n\tat Main.main(Main.java:5)", 1);
        }
        if (code.contains("FORCE_WA")) {
            return JudgeOutcome.wrongAnswer(Math.max(0, totalScore / 2),
                    randomCost(problem), 2048, 1);
        }

        // 默认通过
        return JudgeOutcome.accepted(totalScore, randomCost(problem), 2048);
    }

    private int randomCost(Problem problem) {
        int limit = problem.getTimeLimit() == null ? 1000 : problem.getTimeLimit();
        return ThreadLocalRandom.current().nextInt(10, Math.max(20, limit / 4));
    }

    private void simulateCost() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(300, 800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
