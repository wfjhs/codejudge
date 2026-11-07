package com.codejudge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codejudge.common.context.UserContext;
import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.PageResult;
import com.codejudge.common.result.ResultCode;
import com.codejudge.dao.mapper.ProblemMapper;
import com.codejudge.dao.mapper.SubmissionMapper;
import com.codejudge.dao.mapper.TestCaseMapper;
import com.codejudge.dao.mapper.UserMapper;
import com.codejudge.model.dto.SubmitDTO;
import com.codejudge.model.dto.SubmissionQueryDTO;
import com.codejudge.model.entity.Problem;
import com.codejudge.model.entity.Submission;
import com.codejudge.model.entity.TestCase;
import com.codejudge.model.entity.User;
import com.codejudge.model.enums.Language;
import com.codejudge.model.enums.SubmissionStatus;
import com.codejudge.model.judge.JudgeCompletedEvent;
import com.codejudge.model.judge.JudgeOutcome;
import com.codejudge.model.judge.JudgeTask;
import com.codejudge.model.vo.SubmissionDetailVO;
import com.codejudge.model.vo.SubmissionVO;
import com.codejudge.service.LeaderboardService;
import com.codejudge.service.SubmissionService;
import com.codejudge.service.judge.JudgeEngine;
import com.codejudge.service.judge.JudgeTaskProducer;
import com.codejudge.service.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交与判题服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final UserMapper userMapper;
    private final JudgeEngine judgeEngine;
    private final JudgeTaskProducer taskProducer;
    private final TransactionTemplate transactionTemplate;
    private final RateLimiter rateLimiter;
    private final LeaderboardService leaderboardService;
    private final ApplicationEventPublisher eventPublisher;

    /** 全局提交限流键 */
    private static final String GLOBAL_RL_KEY = "rl:submit:global";

    // ==================== 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(SubmitDTO dto) {
        Language language = Language.of(dto.getLanguage());
        if (language == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的编程语言：" + dto.getLanguage());
        }

        // 限流必须放在任何 DB 写入之前：被拦下的请求连一条 PENDING 记录都不该产生，
        // 否则限流就只剩"慢一点报错"，失去了保护数据库的意义。
        // 三级令牌桶：全局（保系统）/ 用户（防单用户刷）/ 题目（防某题被集火）。
        Long userId = UserContext.requireUserId();
        if (!rateLimiter.tryAcquire(GLOBAL_RL_KEY, 200, 400)) {
            throw new BizException(ResultCode.SUBMIT_TOO_FREQUENT);
        }
        if (!rateLimiter.tryAcquire("rl:submit:u:" + userId, 5, 10)) {
            throw new BizException(ResultCode.SUBMIT_TOO_FREQUENT);
        }
        if (dto.getProblemId() != null
                && !rateLimiter.tryAcquire("rl:submit:p:" + dto.getProblemId(), 20, 40)) {
            throw new BizException(ResultCode.SUBMIT_TOO_FREQUENT);
        }

        Problem problem = problemMapper.selectById(dto.getProblemId());
        if (problem == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND);
        }

        Submission submission = new Submission();
        submission.setRequestId(dto.getRequestId());
        submission.setUserId(userId);
        submission.setProblemId(dto.getProblemId());
        submission.setLanguage(language.getCode());
        submission.setCode(dto.getCode());
        submission.setStatus(SubmissionStatus.PENDING.name());
        submission.setScore(0);
        submission.setSubmitTime(LocalDateTime.now());

        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_request_id 拦下了重复请求。
            // 这里不是错误：说明前端重试了同一个 requestId，直接把原来那条的 ID 还给它。
            Submission exist = submissionMapper.selectOne(
                    new LambdaQueryWrapper<Submission>().eq(Submission::getRequestId, dto.getRequestId()));
            if (exist != null) {
                log.info("重复提交被幂等拦截：requestId={}, 返回原提交 id={}", dto.getRequestId(), exist.getId());
                return exist.getId();
            }
            throw new BizException(ResultCode.ERROR, "提交失败，请重试");
        }

        // 提交数在"提交"这一刻就 +1，而不是判题完成时：用户提交成功就该计数
        problemMapper.increaseSubmitCount(problem.getId());

        // 投递队列。投递失败会抛异常，事务回滚，用户看到的是"提交失败"，
        // 而不是一条永远卡在 PENDING 的脏数据。
        taskProducer.submit(new JudgeTask(
                submission.getId(), problem.getId(), submission.getUserId(), language.getCode()));

        log.info("提交成功：id={}, userId={}, problemId={}, language={}",
                submission.getId(), submission.getUserId(), problem.getId(), language.getCode());
        return submission.getId();
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<SubmissionVO> mySubmissions(SubmissionQueryDTO query) {
        query.normalize();
        Long userId = UserContext.requireUserId();

        // 排序用 submit_time DESC 而不是 id DESC：索引 idx_user_time(user_id, submit_time)
        // 可以直接反向扫描，不用 filesort。
        // InnoDB 二级索引叶子节点自带主键，所以 "submit_time DESC, id DESC" 也能吃到同一个索引。
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, userId)
                .eq(query.getProblemId() != null, Submission::getProblemId, query.getProblemId())
                .eq(StringUtils.hasText(query.getStatus()), Submission::getStatus, query.getStatus())
                .orderByDesc(Submission::getSubmitTime)
                .orderByDesc(Submission::getId);

        // 游标模式：不查总数，直接定位到上一页末尾之后
        if (query.useCursor()) {
            return cursorPage(wrapper, query);
        }

        Page<Submission> page = submissionMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<SubmissionVO> records = page.getRecords().stream().map(this::toVO).toList();
        fillProblemTitles(records, page.getRecords());

        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 游标分页：{@code WHERE (submit_time, id) < (lastTime, lastId)}。
     * <p>
     * 与 offset 分页的本质区别：offset 要"数过前 N 条再丢掉"，页码越深扫描越多
     * （{@code LIMIT 100000, 15} 实际会读出 100015 条再丢弃前 100000 条）；
     * 游标直接按索引定位到起点，耗时与页码无关——这才是深分页优化的核心。
     * <p>
     * 两个细节：
     * <ul>
     *     <li><b>复合游标</b>：只看 id 会在 submit_time 相同的记录上漏数据或重复；</li>
     *     <li><b>多取一条</b>判断 hasMore，避免额外一次 COUNT——深分页下 COUNT 往往比查询本身还慢。</li>
     * </ul>
     */
    private PageResult<SubmissionVO> cursorPage(LambdaQueryWrapper<Submission> wrapper,
                                                SubmissionQueryDTO query) {
        long size = query.getSize();
        LocalDateTime cursorTime = query.lastTimeAsLocalDateTime();
        wrapper.and(w -> w
                        .lt(Submission::getSubmitTime, cursorTime)
                        .or(o -> o.eq(Submission::getSubmitTime, cursorTime)
                                .lt(Submission::getId, query.getLastId())))
                .last("LIMIT " + (size + 1));

        List<Submission> rows = submissionMapper.selectList(wrapper);
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, (int) size);
        }

        List<SubmissionVO> records = rows.stream().map(this::toVO).toList();
        fillProblemTitles(records, rows);
        return PageResult.cursor(records, size, hasMore);
    }

    @Override
    public SubmissionDetailVO detail(Long id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BizException(ResultCode.SUBMISSION_NOT_FOUND);
        }
        // 越权防护：只能看自己的提交。管理员看别人的后面用单独接口实现。
        if (!submission.getUserId().equals(UserContext.requireUserId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }

        SubmissionDetailVO vo = new SubmissionDetailVO();
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setLanguage(submission.getLanguage());
        vo.setCode(submission.getCode());
        vo.setStatus(submission.getStatus());
        vo.setScore(submission.getScore());
        vo.setTimeCost(submission.getTimeCost());
        vo.setMemoryCost(submission.getMemoryCost());
        vo.setErrorMsg(submission.getErrorMsg());
        vo.setFailCase(submission.getFailCase());
        vo.setSubmitTime(submission.getSubmitTime());
        vo.setJudgeTime(submission.getJudgeTime());

        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
        }

        Long caseCount = testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getProblemId, submission.getProblemId()));
        vo.setTotalCase(caseCount == null ? 0 : caseCount.intValue());

        return vo;
    }

    // ==================== 判题 ====================

    @Override
    public void judge(JudgeTask task) {
        Long submissionId = task.getSubmissionId();
        if (submissionId == null) {
            log.warn("丢弃非法判题任务：{}", task);
            return;
        }

        // ---- 第一步：抢占 ----
        // 带 status='PENDING' 条件的更新就是整个幂等机制的支点：
        // 同一条消息被重复消费时，第二次的 rows 会是 0，直接返回。
        // 这比分布式锁简单得多，也不会有锁超时/脑裂问题。
        int rows = submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                .eq(Submission::getId, submissionId)
                .eq(Submission::getStatus, SubmissionStatus.PENDING.name())
                .set(Submission::getStatus, SubmissionStatus.JUDGING.name()));

        if (rows == 0) {
            log.info("跳过判题（已被处理或不存在）：submissionId={}", submissionId);
            return;
        }

        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            // 消息投递后事务回滚的极端情况：记录不存在，直接丢弃
            log.warn("提交记录不存在，丢弃任务：submissionId={}", submissionId);
            return;
        }

        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null) {
            // 题目被删了，判不了，标记为系统错误，避免永远卡在 JUDGING
            writeBack(submission, JudgeOutcome.of(SubmissionStatus.SE, "题目已不存在", null));
            return;
        }

        List<TestCase> cases = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, problem.getId())
                        .orderByAsc(TestCase::getSortOrder, TestCase::getId));

        if (cases.isEmpty()) {
            writeBack(submission, JudgeOutcome.of(SubmissionStatus.SE, "该题目没有配置测试用例", null));
            return;
        }

        // ---- 第二步：判题（不放在事务里）----
        // 判题可能耗时几百毫秒到几秒，绝不能包在事务里：
        // 长事务会一直占着数据库连接，20 个连接池很快就被打满。
        JudgeOutcome outcome;
        try {
            outcome = judgeEngine.judge(submission, problem, cases);
        } catch (Exception e) {
            log.error("判题引擎异常：submissionId={}", submissionId, e);
            outcome = JudgeOutcome.of(SubmissionStatus.SE, "判题机内部错误", null);
        }

        // ---- 第三步：回写（短事务）----
        writeBack(submission, outcome);

        // 回写完成后发布"判题完成"事件：cj-web 的 WebSocket 层监听，实时把结果推给前端。
        // 用事件而不是直接调用 WS：保持 cj-service 不反向依赖 cj-web，模块边界干净。
        eventPublisher.publishEvent(new JudgeCompletedEvent(
                submission.getId(), outcome.status(), outcome.score(),
                outcome.timeCost(), outcome.memoryCost(), outcome.errorMsg(), outcome.failCase()));
    }

    /**
     * 判题结果回写。用 TransactionTemplate 而不是 @Transactional，
     * 原因是这里属于"同类内部调用"——@Transactional 走代理，自调用会失效。
     */
    private void writeBack(Submission submission, JudgeOutcome outcome) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Submission update = new Submission();
                update.setId(submission.getId());
                update.setStatus(outcome.status().name());
                update.setScore(outcome.score());
                update.setTimeCost(outcome.timeCost());
                update.setMemoryCost(outcome.memoryCost());
                update.setErrorMsg(truncate(outcome.errorMsg(), 2000));
                update.setFailCase(outcome.failCase());
                update.setJudgeTime(LocalDateTime.now());
                submissionMapper.updateById(update);

                if (outcome.status().isAccepted()) {
                    problemMapper.increaseAcceptCount(submission.getProblemId());
                    increaseSolvedIfFirstAccept(submission);
                }
            });
            log.info("判题完成：submissionId={}, status={}, score={}",
                    submission.getId(), outcome.status(), outcome.score());
        } catch (Exception e) {
            log.error("回写判题结果失败：submissionId={}", submission.getId(), e);
        }
    }

    /**
     * 首次通过时才给用户的 solved_count +1。
     * <p>
     * <b>已知并发缺陷</b>：两次提交同时判完 AC 时，可能都查到"之前没有 AC"，导致重复 +1。
     * 修法是在这里加一把以 (userId, problemId) 为粒度的分布式锁（Redis SETNX），
     * 属于 M6 限流与幂等的范畴，当前先记录问题不做过度设计。
     */
    private void increaseSolvedIfFirstAccept(Submission submission) {
        Long previousAc = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, submission.getUserId())
                .eq(Submission::getProblemId, submission.getProblemId())
                .eq(Submission::getStatus, SubmissionStatus.AC.name())
                .ne(Submission::getId, submission.getId()));
        if (previousAc == null || previousAc == 0) {
            userMapper.increaseSolvedCount(submission.getUserId());
            // 解题数变了，同步刷新排行榜 ZSet。只在这里更新，写入成本极低。
            User user = userMapper.selectById(submission.getUserId());
            if (user != null) {
                leaderboardService.sync(user.getId(), user.getSolvedCount());
            }
        }
    }

    // ==================== 工具 ====================

    private SubmissionVO toVO(Submission s) {
        SubmissionVO vo = new SubmissionVO();
        vo.setId(s.getId());
        vo.setProblemId(s.getProblemId());
        vo.setLanguage(s.getLanguage());
        vo.setStatus(s.getStatus());
        vo.setScore(s.getScore());
        vo.setTimeCost(s.getTimeCost());
        vo.setMemoryCost(s.getMemoryCost());
        vo.setFailCase(s.getFailCase());
        vo.setSubmitTime(s.getSubmitTime());
        return vo;
    }

    /**
     * 批量补题目名称。
     * <p>
     * 刻意没有用 JOIN 一次查出来，而是"先分页查提交，再用 IN 查题目"：
     * 提交表是最大的表，JOIN 会让分页查询依赖两张表的索引情况；
     * 而题目数量少、命中缓存概率高，两次简单查询反而更稳定。
     */
    private void fillProblemTitles(List<SubmissionVO> records, List<Submission> submissions) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> problemIds = submissions.stream().map(Submission::getProblemId).distinct().toList();
        List<Problem> problems = problemMapper.selectBatchIds(problemIds);
        var titleMap = problems.stream()
                .collect(java.util.stream.Collectors.toMap(Problem::getId, Problem::getTitle, (a, b) -> a));
        records.forEach(vo -> vo.setProblemTitle(titleMap.get(vo.getProblemId())));
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        // 错误信息可能很长（编译器输出），截断避免 TEXT 字段被撑爆
        return text.substring(0, maxLen) + "...(truncated)";
    }
}
