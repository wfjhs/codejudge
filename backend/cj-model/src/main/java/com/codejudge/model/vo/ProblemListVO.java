package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目列表项。
 * <p>
 * <b>刻意不包含 content 字段</b>：题面是 MEDIUMTEXT，单条约几 KB 到几十 KB，
 * 一页 10 条就是几百 KB 的无用传输。列表页只需要标题和统计信息，
 * 详情页才去查 content。
 * <p>
 * 这个取舍在压测时能体现出来：带 content 的列表接口响应体积会大一个数量级。
 */
@Data
public class ProblemListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    /** 1-简单 2-中等 3-困难 */
    private Integer difficulty;

    private Integer submitCount;

    private Integer acceptCount;

    /** 通过率（0-100），保留两位小数。submitCount 为 0 时返回 0 */
    private Double acceptRate;

    private LocalDateTime createTime;
}
