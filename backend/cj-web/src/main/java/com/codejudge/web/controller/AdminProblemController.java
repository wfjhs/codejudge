package com.codejudge.web.controller;

import com.codejudge.common.annotation.RequireAdmin;
import com.codejudge.common.result.PageResult;
import com.codejudge.common.result.Result;
import com.codejudge.model.dto.ProblemQueryDTO;
import com.codejudge.model.dto.ProblemSaveDTO;
import com.codejudge.model.vo.AdminProblemDetailVO;
import com.codejudge.model.vo.ProblemListVO;
import com.codejudge.service.ProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目管理接口。
 * <p>
 * 类上标 {@link RequireAdmin}，整个 Controller 的所有方法都要求管理员身份，
 * 由 AdminInterceptor 统一校验，方法内部不需要再写 if (isAdmin)。
 */
@RestController
@RequestMapping("/api/admin/problems")
@RequiredArgsConstructor
@RequireAdmin
public class AdminProblemController {

    private final ProblemService problemService;

    /**
     * 管理端题目列表。与公开接口共用分页逻辑。
     */
    @GetMapping
    public Result<PageResult<ProblemListVO>> page(ProblemQueryDTO query) {
        return Result.ok(problemService.page(query));
    }

    /**
     * 管理端题目详情，包含全部测试用例（含隐藏用例），用于编辑页回填。
     */
    @GetMapping("/{id}")
    public Result<AdminProblemDetailVO> detail(@PathVariable Long id) {
        return Result.ok(problemService.adminDetail(id));
    }

    /**
     * 创建题目。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProblemSaveDTO dto) {
        return Result.ok(problemService.create(dto));
    }

    /**
     * 更新题目。测试用例全量替换。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProblemSaveDTO dto) {
        problemService.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除题目（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        problemService.delete(id);
        return Result.ok();
    }
}
