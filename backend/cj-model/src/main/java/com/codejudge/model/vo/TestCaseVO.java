package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 测试用例（管理端视角，包含隐藏用例）。
 * <p>
 * 与 {@link SampleCaseVO} 的区别：这个只允许在管理端接口返回，用于编辑题目时回填表单。
 */
@Data
public class TestCaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String input;

    private String output;

    private Integer isSample;

    private Integer score;

    private Integer sortOrder;
}
