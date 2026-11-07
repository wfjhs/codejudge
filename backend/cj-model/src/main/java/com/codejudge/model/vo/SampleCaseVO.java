package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 示例用例（对外可见）。
 * <p>
 * 只包含 is_sample=1 的用例。隐藏用例的 input/output <b>绝对不能</b>返回给前端，
 * 否则用户直接照着期望输出打表就能 AC —— 这是判题系统最基本的安全边界。
 */
@Data
public class SampleCaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String input;

    private String output;
}
