package com.codejudge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息（对外输出）。
 * <p>
 * 与 User 实体分开的原因：实体里有 password 字段，直接返回会造成密码泄露。
 * 用 VO 做一次显式转换，是要刻意保留的动作，不能图省事直接返回实体。
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private Integer role;

    private Integer solvedCount;

    private LocalDateTime createTime;
}
