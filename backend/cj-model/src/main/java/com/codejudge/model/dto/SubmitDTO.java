package com.codejudge.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交代码请求。
 */
@Data
public class SubmitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "题目 ID 不能为空")
    private Long problemId;

    @NotBlank(message = "请选择编程语言")
    private String language;

    @NotBlank(message = "代码不能为空")
    @Size(max = 65535, message = "代码长度不能超过 65535 字符")
    private String code;

    /**
     * 幂等键，由前端在用户点击提交时生成（UUID），网络重试时复用同一个值。
     * <p>
     * 服务端用它做两件事：Redis 快速去重 + 数据库唯一索引兜底。
     * 前端不传的话服务端会自己生成一个，但那样就失去了"重试幂等"的意义。
     */
    @NotBlank(message = "requestId 不能为空")
    @Size(max = 64, message = "requestId 长度不能超过 64")
    private String requestId;
}
