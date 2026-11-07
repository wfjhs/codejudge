package com.codejudge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 测试用例。
 * <p>
 * 没有逻辑删除字段：用例是题目的附属数据，更新题目时整组替换，
 * 不需要单独做逻辑删除（历史上也不用追溯）。
 */
@Data
@TableName("test_case")
public class TestCase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long problemId;

    private String input;

    private String output;

    /** 1-示例用例（前端可见） 0-隐藏用例 */
    private Integer isSample;

    private Integer score;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
