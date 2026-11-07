package com.codejudge.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目表。
 */
@Data
@TableName("problem")
public class Problem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 题面，Markdown 格式，MEDIUMTEXT 大字段 */
    private String content;

    /** 1-简单 2-中等 3-困难 */
    private Integer difficulty;

    /** 时间限制（毫秒） */
    private Integer timeLimit;

    /** 内存限制（MB） */
    private Integer memoryLimit;

    private Integer submitCount;

    private Integer acceptCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
