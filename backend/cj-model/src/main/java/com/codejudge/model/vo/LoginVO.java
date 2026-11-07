package com.codejudge.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录成功返回。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String token;

    /** token 有效期（秒），前端可据此做无感刷新 */
    private Long expireIn;

    private UserVO user;
}
