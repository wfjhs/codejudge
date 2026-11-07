package com.codejudge.service.judge;

import com.codejudge.model.entity.Problem;
import com.codejudge.model.entity.Submission;
import com.codejudge.model.entity.TestCase;
import com.codejudge.model.judge.JudgeOutcome;

import java.util.List;

/**
 * 判题引擎。
 * <p>
 * <b>这是整个项目最重要的一个抽象</b>：把"怎么执行代码"从"怎么调度判题"里剥离出来。
 * <ul>
 *     <li>M3 阶段用 {@link MockJudgeEngine} 跑通链路（不真正执行代码）</li>
 *     <li>M4 阶段换成进程级沙箱实现</li>
 *     <li>如果后面装了 Docker，再换容器级实现</li>
 * </ul>
 * 上层（SubmissionService、Stream 消费者）完全不需要改一行代码。
 * <p>
 * 注意：实现类必须自己保证<b>不抛异常</b>，任何执行失败都要翻译成
 * {@link com.codejudge.model.enums.SubmissionStatus#SE} 返回，
 * 否则一条坏消息会卡住整个消费循环。
 */
public interface JudgeEngine {

    /**
     * 执行判题。
     *
     * @param submission 提交记录（含源码）
     * @param problem    题目（含时间/内存限制）
     * @param testCases  全部用例（含隐藏用例，只有服务端拿得到）
     * @return 判题结果，永远是终态
     */
    JudgeOutcome judge(Submission submission, Problem problem, List<TestCase> testCases);
}
