package com.codejudge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提交记录。
 * <p>
 * 没有逻辑删除字段：提交记录是审计数据，删掉就失去意义。
 */
@Data
@TableName("submission")
public class Submission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 前端生成的幂等键，重试时复用同一个值 */
    private String requestId;

    private Long userId;

    private Long problemId;

    /** JAVA / PYTHON */
    private String language;

    private String code;

    /** 见 SubmissionStatus */
    private String status;

    private Integer score;

    /** 最大用例耗时（ms） */
    private Integer timeCost;

    /** 峰值内存（KB） */
    private Integer memoryCost;

    /** 编译/运行错误信息 */
    private String errorMsg;

    /** 第一个失败用例的序号，方便用户定位 */
    private Integer failCase;

    private LocalDateTime submitTime;

    private LocalDateTime judgeTime;
}
