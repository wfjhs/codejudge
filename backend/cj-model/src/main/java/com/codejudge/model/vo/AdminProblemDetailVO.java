package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情（管理端视角）。
 * <p>
 * 比 {@link ProblemDetailVO} 多返回全部测试用例（含隐藏用例），用于编辑页回填。
 * 必须配合 {@code @RequireAdmin} 使用，不能出现在公开接口上。
 */
@Data
public class AdminProblemDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String content;

    private Integer difficulty;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer submitCount;

    private Integer acceptCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 全部用例，含隐藏 */
    private List<TestCaseVO> cases;
}
