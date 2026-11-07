package com.codejudge.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 测试用例的入参。
 */
@Data
public class TestCaseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "用例输入不能为空")
    private String input;

    @NotNull(message = "用例期望输出不能为空")
    private String output;

    /** 是否示例用例：1-是（前端可见） 0-否（隐藏） */
    @NotNull(message = "请指定是否为示例用例")
    @Min(value = 0, message = "isSample 只能是 0 或 1")
    @Max(value = 1, message = "isSample 只能是 0 或 1")
    private Integer isSample;

    /** 分值，隐藏用例按分值累加得出总分 */
    @Min(value = 0, message = "分值不能为负")
    private Integer score = 0;
}
