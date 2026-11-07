package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提交记录详情。
 * <p>
 * 比列表项多了 code 和 errorMsg：用户点进某条记录时要看到自己写的源码和失败原因。
 */
@Data
public class SubmissionDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long problemId;

    private String problemTitle;

    private String language;

    private String code;

    private String status;

    private Integer score;

    private Integer timeCost;

    private Integer memoryCost;

    private String errorMsg;

    private Integer failCase;

    /** 用例总数，前端可以展示"通过 3/5 个测试点" */
    private Integer totalCase;

    private LocalDateTime submitTime;

    private LocalDateTime judgeTime;
}
