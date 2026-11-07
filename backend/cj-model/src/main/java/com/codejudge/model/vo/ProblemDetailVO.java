package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情。
 * <p>
 * 只有这个 VO 带 content（大字段），并且只附带示例用例。
 */
@Data
public class ProblemDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    /** 题面，Markdown */
    private String content;

    private Integer difficulty;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer submitCount;

    private Integer acceptCount;

    private Double acceptRate;

    private LocalDateTime createTime;

    /** 示例用例，仅 is_sample=1 */
    private List<SampleCaseVO> samples;
}
