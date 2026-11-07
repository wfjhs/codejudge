package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提交记录列表项。
 * <p>
 * 不含 code：提交列表一页 10 条，每条源码可能几 KB，全带上会让响应体膨胀。
 * 源码只在详情接口返回。
 */
@Data
public class SubmissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long problemId;

    private String problemTitle;

    private String language;

    /** PENDING / JUDGING / AC / WA / TLE / MLE / RE / CE / SE */
    private String status;

    private Integer score;

    private Integer timeCost;

    private Integer memoryCost;

    private Integer failCase;

    private LocalDateTime submitTime;
}
