package com.codejudge.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目分页查询参数。
 * <p>
 * 用 GET 查询参数传递，字段都有默认值，前端不传也不会出错。
 */
@Data
public class ProblemQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    private Long page = 1L;

    /** 每页条数，最大 100（由 MyBatis-Plus 分页插件兜底限制） */
    private Long size = 10L;

    /** 难度筛选：1-简单 2-中等 3-困难，null 表示不限 */
    private Integer difficulty;

    /** 标题关键字，模糊匹配 */
    private String keyword;

    /** 排序字段：id / difficulty / submitCount，默认 id */
    private String sortBy = "id";

    /** 是否升序，默认 false（按 id 倒序，新题在前） */
    private Boolean asc = false;

    /**
     * 归一化分页参数。
     * <p>
     * 不能信任前端传的值：page=0 或负数会让 offset 计算异常，size=0 会查出空列表。
     */
    public void normalize() {
        if (page == null || page < 1) {
            page = 1L;
        }
        if (size == null || size < 1) {
            size = 10L;
        }
        if (size > 100) {
            size = 100L;
        }
    }
}
