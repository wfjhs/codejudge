package com.codejudge.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目的创建/更新入参。
 * <p>
 * 创建和更新共用一个 DTO，区别只在 Controller 的路径：更新时 id 从 path 上取。
 * 这样避免了两个几乎相同的类，前端也只需要写一套表单。
 */
@Data
public class ProblemSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "题目标题不能为空")
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "题面内容不能为空")
    private String content;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度只能是 1-简单 2-中等 3-困难")
    @Max(value = 3, message = "难度只能是 1-简单 2-中等 3-困难")
    private Integer difficulty;

    /** 时间限制（毫秒），默认 1000 */
    @Min(value = 100, message = "时间限制不能小于 100ms")
    @Max(value = 10000, message = "时间限制不能大于 10000ms")
    private Integer timeLimit = 1000;

    /** 内存限制（MB），默认 256 */
    @Min(value = 16, message = "内存限制不能小于 16MB")
    @Max(value = 1024, message = "内存限制不能大于 1024MB")
    private Integer memoryLimit = 256;

    /**
     * 测试用例。
     * <p>
     * 至少要有一个示例用例，否则前端题目详情页没有任何样例可供用户自测。
     * <b>注意</b>：更新时是全量替换 —— 先删掉该题所有旧用例再插入新的。
     */
    @Valid
    @NotEmpty(message = "至少需要一个测试用例")
    private List<TestCaseDTO> cases;
}
