package com.codejudge.web.controller;

import com.codejudge.common.result.PageResult;
import com.codejudge.common.result.Result;
import com.codejudge.model.dto.ProblemQueryDTO;
import com.codejudge.model.vo.ProblemDetailVO;
import com.codejudge.model.vo.ProblemListVO;
import com.codejudge.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目接口（公开，需登录）。
 * <p>
 * 注意这里是"需登录"而不是"免登录"：JwtInterceptor 拦截 /api/**，
 * 只有 /api/auth/** 被排除。浏览题目要登录，是 OJ 的常规做法。
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    /**
     * 题目列表（分页 + 难度/关键字筛选）。不返回题面内容。
     */
    @GetMapping
    public Result<PageResult<ProblemListVO>> page(ProblemQueryDTO query) {
        return Result.ok(problemService.page(query));
    }

    /**
     * 题目详情。只返回示例用例，隐藏用例不会出现在响应里。
     */
    @GetMapping("/{id}")
    public Result<ProblemDetailVO> detail(@PathVariable Long id) {
        return Result.ok(problemService.detail(id));
    }
}
