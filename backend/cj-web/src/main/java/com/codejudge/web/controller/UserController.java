package com.codejudge.web.controller;

import com.codejudge.common.result.Result;
import com.codejudge.model.vo.UserVO;
import com.codejudge.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（需 token）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 当前登录用户信息。前端进入页面时用它校验 token 是否仍然有效。
     */
    @GetMapping("/me")
    public Result<UserVO> currentUser() {
        return Result.ok(userService.getCurrentUser());
    }

    /**
     * 查看指定用户。
     */
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }
}
