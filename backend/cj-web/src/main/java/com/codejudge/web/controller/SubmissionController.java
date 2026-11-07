package com.codejudge.web.controller;

import com.codejudge.common.result.PageResult;
import com.codejudge.common.result.Result;
import com.codejudge.model.dto.SubmitDTO;
import com.codejudge.model.dto.SubmissionQueryDTO;
import com.codejudge.model.vo.SubmissionDetailVO;
import com.codejudge.model.vo.SubmissionVO;
import com.codejudge.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提交与判题接口。
 * <p>
 * 提交接口是<b>异步</b>的：返回 202 语义（虽然 HTTP 状态码统一是 200，见 GlobalExceptionHandler），
 * 前端拿到 submissionId 后去轮询或等 WebSocket 推送结果。
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * 提交代码。立即返回 submissionId，实际判题在后台队列里进行。
     */
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody SubmitDTO dto) {
        return Result.ok(submissionService.submit(dto));
    }

    /**
     * 我的提交记录（分页），可按题目或状态筛选。
     */
    @GetMapping
    public Result<PageResult<SubmissionVO>> mySubmissions(SubmissionQueryDTO query) {
        return Result.ok(submissionService.mySubmissions(query));
    }

    /**
     * 提交详情，含源码和错误信息。只能查看自己的提交。
     * <p>
     * 路径变量限定为纯数字：不加约束时，任何非数字路径（例如前端曾误写成
     * {@code /submissions/my}）都会被这个映射捕获，然后在参数类型转换阶段抛
     * {@code MethodArgumentTypeMismatchException}——一个本该 404 的请求变成 500，
     * 还把完整堆栈打进日志。约束之后这类路径直接不匹配，返回干净的 404。
     */
    @GetMapping("/{id:\\d+}")
    public Result<SubmissionDetailVO> detail(@PathVariable Long id) {
        return Result.ok(submissionService.detail(id));
    }
}
