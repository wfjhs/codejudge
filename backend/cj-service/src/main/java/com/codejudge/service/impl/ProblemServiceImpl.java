package com.codejudge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.PageResult;
import com.codejudge.common.result.ResultCode;
import com.codejudge.dao.mapper.ProblemMapper;
import com.codejudge.dao.mapper.TestCaseMapper;
import com.codejudge.model.dto.ProblemQueryDTO;
import com.codejudge.model.dto.ProblemSaveDTO;
import com.codejudge.model.dto.TestCaseDTO;
import com.codejudge.model.entity.Problem;
import com.codejudge.model.entity.TestCase;
import com.codejudge.model.vo.AdminProblemDetailVO;
import com.codejudge.model.vo.ProblemDetailVO;
import com.codejudge.model.vo.ProblemListVO;
import com.codejudge.model.vo.SampleCaseVO;
import com.codejudge.model.vo.TestCaseVO;
import com.codejudge.service.ProblemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

/**
 * 题目服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 题目详情缓存前缀；TTL 5 分钟 */
    private static final String DETAIL_CACHE_PREFIX = "problem:detail:";
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(5);

    @Override
    public PageResult<ProblemListVO> page(ProblemQueryDTO query) {
        query.normalize();

        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                // 关键：列表页只查轻量字段，不查 content
                // content 是 MEDIUMTEXT，一页 10 条可能几百 KB，全是无用传输
                .select(Problem::getId,
                        Problem::getTitle,
                        Problem::getDifficulty,
                        Problem::getSubmitCount,
                        Problem::getAcceptCount,
                        Problem::getCreateTime)
                .eq(query.getDifficulty() != null, Problem::getDifficulty, query.getDifficulty())
                .like(StringUtils.hasText(query.getKeyword()), Problem::getTitle, query.getKeyword());

        // 排序字段用白名单映射，绝不拼接前端传来的字符串（SQL 注入）
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        switch (query.getSortBy() == null ? "id" : query.getSortBy()) {
            case "difficulty" -> wrapper.orderBy(true, asc, Problem::getDifficulty);
            case "submitCount" -> wrapper.orderBy(true, asc, Problem::getSubmitCount);
            case "acceptCount" -> wrapper.orderBy(true, asc, Problem::getAcceptCount);
            default -> wrapper.orderBy(true, asc, Problem::getId);
        }

        Page<Problem> page = problemMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<ProblemListVO> records = page.getRecords().stream()
                .map(this::toListVO)
                .toList();

        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ProblemDetailVO detail(Long id) {
        // ---- 缓存读（cache-aside）----
        // 命中就直接返回，连一道题的 SELECT 都省了。OJ 详情页是读多写少的典型场景，
        // 热门题（比赛题）会被反复拉取，缓存能把数据库压力打到接近 0。
        String cacheKey = DETAIL_CACHE_PREFIX + id;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                ProblemDetailVO vo = objectMapper.readValue(cached, ProblemDetailVO.class);
                // 缓存里存的是重的大字段（题面/示例用例），但 submitCount/acceptCount 是实时变化的。
                // 如果连统计值一起缓存，用户刚 AC 完刷新页面还看到旧的通过数，体验很差。
                // 所以缓存命中后用一次"仅查两列"的主键轻查询，把统计值覆盖成最新的。
                overlayLiveStats(vo);
                return vo;
            }
        } catch (Exception e) {
            // 缓存读失败绝不能影响主流程：当作未命中，回源数据库
            log.warn("读取题目缓存失败，回源数据库：id={}, {}", id, e.getMessage());
        }

        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND);
        }

        ProblemDetailVO vo = new ProblemDetailVO();
        vo.setId(problem.getId());
        vo.setTitle(problem.getTitle());
        vo.setContent(problem.getContent());
        vo.setDifficulty(problem.getDifficulty());
        vo.setTimeLimit(problem.getTimeLimit());
        vo.setMemoryLimit(problem.getMemoryLimit());
        vo.setSubmitCount(problem.getSubmitCount());
        vo.setAcceptCount(problem.getAcceptCount());
        vo.setAcceptRate(calcAcceptRate(problem.getSubmitCount(), problem.getAcceptCount()));
        vo.setCreateTime(problem.getCreateTime());

        // 只查示例用例：隐藏用例的输入输出泄露 = 用户可以直接打表 AC
        List<TestCase> samples = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, id)
                        .eq(TestCase::getIsSample, 1)
                        .orderByAsc(TestCase::getSortOrder, TestCase::getId));

        vo.setSamples(samples.stream().map(tc -> {
            SampleCaseVO s = new SampleCaseVO();
            s.setInput(tc.getInput());
            s.setOutput(tc.getOutput());
            return s;
        }).toList());

        // ---- 缓存写 ----
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(vo), DETAIL_CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入题目缓存失败（忽略）：id={}, {}", id, e.getMessage());
        }

        return vo;
    }

    @Override
    public AdminProblemDetailVO adminDetail(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND);
        }

        AdminProblemDetailVO vo = new AdminProblemDetailVO();
        vo.setId(problem.getId());
        vo.setTitle(problem.getTitle());
        vo.setContent(problem.getContent());
        vo.setDifficulty(problem.getDifficulty());
        vo.setTimeLimit(problem.getTimeLimit());
        vo.setMemoryLimit(problem.getMemoryLimit());
        vo.setSubmitCount(problem.getSubmitCount());
        vo.setAcceptCount(problem.getAcceptCount());
        vo.setCreateTime(problem.getCreateTime());
        vo.setUpdateTime(problem.getUpdateTime());

        List<TestCase> cases = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, id)
                        .orderByAsc(TestCase::getSortOrder, TestCase::getId));

        vo.setCases(cases.stream().map(this::toTestCaseVO).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProblemSaveDTO dto) {
        Problem problem = new Problem();
        problem.setTitle(dto.getTitle());
        problem.setContent(dto.getContent());
        problem.setDifficulty(dto.getDifficulty());
        problem.setTimeLimit(dto.getTimeLimit());
        problem.setMemoryLimit(dto.getMemoryLimit());
        problem.setSubmitCount(0);
        problem.setAcceptCount(0);

        problemMapper.insert(problem);
        saveCases(problem.getId(), dto.getCases());

        log.info("创建题目成功：id={}, title={}, 用例数={}",
                problem.getId(), problem.getTitle(), dto.getCases().size());
        return problem.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProblemSaveDTO dto) {
        Problem exist = problemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND);
        }

        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle(dto.getTitle());
        problem.setContent(dto.getContent());
        problem.setDifficulty(dto.getDifficulty());
        problem.setTimeLimit(dto.getTimeLimit());
        problem.setMemoryLimit(dto.getMemoryLimit());
        // submitCount / acceptCount 不在这里改：它们是判题结果累积出来的统计值，
        // 管理端编辑题面不应该把它们清零

        problemMapper.updateById(problem);

        // 题面改了，缓存的旧详情必须失效，否则用户看到的是更新前的版本
        evictDetailCache(id);

        // 用例全量替换：先删后插。
        // 用例没有逻辑删除字段，这里是物理删除 —— 用例属于题目附属数据，
        // 旧用例本身就是被新用例替代掉的，没有保留价值。
        testCaseMapper.delete(new LambdaQueryWrapper<TestCase>().eq(TestCase::getProblemId, id));
        saveCases(id, dto.getCases());

        log.info("更新题目成功：id={}, 用例数={}", id, dto.getCases().size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Problem exist = problemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND);
        }
        // 逻辑删除，用例保留（题目数据本身还在库里，用例跟着保留才完整）
        problemMapper.deleteById(id);
        // 逻辑删除后详情接口会 404，但缓存里可能还存着旧数据，先清掉避免脏读
        evictDetailCache(id);
        log.info("删除题目（逻辑删除）：id={}", id);
    }

    // ==================== 私有方法 ====================

    private void evictDetailCache(Long id) {
        try {
            redisTemplate.delete(DETAIL_CACHE_PREFIX + id);
        } catch (Exception e) {
            log.warn("清除题目缓存失败（忽略）：id={}, {}", id, e.getMessage());
        }
    }

    /**
     * 用主键轻查询覆盖统计值（只 select 两列，不加载 content 大字段）。
     * <p>
     * 这是"缓存重字段 + 实时统计走轻查询"的落地：既保住了缓存对大字段的收益，
     * 又保证用户提交/通过后看到的数字是实时的。
     */
    private void overlayLiveStats(ProblemDetailVO vo) {
        try {
            Problem stat = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                    .select(Problem::getSubmitCount, Problem::getAcceptCount)
                    .eq(Problem::getId, vo.getId()));
            if (stat != null) {
                vo.setSubmitCount(stat.getSubmitCount());
                vo.setAcceptCount(stat.getAcceptCount());
                vo.setAcceptRate(calcAcceptRate(stat.getSubmitCount(), stat.getAcceptCount()));
            }
        } catch (Exception e) {
            // 统计值覆盖失败不影响题面展示，用缓存里的旧值兜底
            log.warn("覆盖题目实时统计失败（忽略）：id={}, {}", vo.getId(), e.getMessage());
        }
    }

    private void saveCases(Long problemId, List<TestCaseDTO> cases) {
        // 用例数量在 10 条量级，逐条 insert 足够。
        // 如果以后要做到几百条，应该改成批量插入（jdbc url 已开 rewriteBatchedStatements=true）
        int index = 0;
        for (TestCaseDTO c : cases) {
            TestCase tc = new TestCase();
            tc.setProblemId(problemId);
            tc.setInput(c.getInput());
            tc.setOutput(c.getOutput());
            tc.setIsSample(c.getIsSample());
            tc.setScore(c.getScore() == null ? 0 : c.getScore());
            // sortOrder 沿用提交顺序，保证示例用例的展示顺序和管理端一致
            tc.setSortOrder(index++);
            testCaseMapper.insert(tc);
        }
    }

    private ProblemListVO toListVO(Problem p) {
        ProblemListVO vo = new ProblemListVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDifficulty(p.getDifficulty());
        vo.setSubmitCount(p.getSubmitCount());
        vo.setAcceptCount(p.getAcceptCount());
        vo.setAcceptRate(calcAcceptRate(p.getSubmitCount(), p.getAcceptCount()));
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    private TestCaseVO toTestCaseVO(TestCase tc) {
        TestCaseVO vo = new TestCaseVO();
        vo.setId(tc.getId());
        vo.setInput(tc.getInput());
        vo.setOutput(tc.getOutput());
        vo.setIsSample(tc.getIsSample());
        vo.setScore(tc.getScore());
        vo.setSortOrder(tc.getSortOrder());
        return vo;
    }

    /**
     * 通过率 = 通过次数 / 提交次数 × 100，保留两位小数。
     * 注意 submitCount 为 0 时要直接返回 0，不能做除法。
     */
    private Double calcAcceptRate(Integer submitCount, Integer acceptCount) {
        if (submitCount == null || submitCount == 0) {
            return 0.0;
        }
        int accept = acceptCount == null ? 0 : acceptCount;
        return BigDecimal.valueOf(accept)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(submitCount), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
