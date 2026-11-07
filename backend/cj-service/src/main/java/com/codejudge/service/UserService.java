package com.codejudge.service;

import com.codejudge.model.dto.LoginDTO;
import com.codejudge.model.dto.RegisterDTO;
import com.codejudge.model.vo.LoginVO;
import com.codejudge.model.vo.UserVO;

/**
 * 用户服务。
 */
public interface UserService {

    /**
     * 注册。
     *
     * @return 新用户 ID
     */
    Long register(RegisterDTO dto);

    /**
     * 登录。
     */
    LoginVO login(LoginDTO dto);

    /**
     * 获取当前登录用户信息。
     */
    UserVO getCurrentUser();

    /**
     * 按 ID 查询用户信息。
     */
    UserVO getById(Long id);
}
