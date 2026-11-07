package com.codejudge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codejudge.common.constant.AuthConstants;
import com.codejudge.common.context.CurrentUser;
import com.codejudge.common.context.UserContext;
import com.codejudge.common.exception.BizException;
import com.codejudge.common.result.ResultCode;
import com.codejudge.common.util.JwtUtil;
import com.codejudge.dao.mapper.UserMapper;
import com.codejudge.model.dto.LoginDTO;
import com.codejudge.model.dto.RegisterDTO;
import com.codejudge.model.entity.User;
import com.codejudge.model.vo.LoginVO;
import com.codejudge.model.vo.UserVO;
import com.codejudge.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        // 先查一次是为了给出友好提示，但不能依赖它做唯一性保证：
        // 两个请求同时通过这个 if，仍然会插入两条同名记录。
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        // BCrypt 自带随机盐，同一密码每次加密结果都不同，无法被彩虹表反查
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole(resolveRole());
        user.setSolvedCount(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_username 才是真正的并发防线，这里把数据库异常翻译成业务异常
            log.warn("并发注册拦截：username={}", dto.getUsername());
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        log.info("新用户注册成功：id={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
        return user.getId();
    }

    /**
     * 系统初始化时第一个注册的用户自动成为管理员，省去手工插入初始账号。
     * 真实项目里应该由运维在部署时指定，这里是为了"克隆下来就能用"。
     */
    private Integer resolveRole() {
        Long total = userMapper.selectCount(null);
        return (total == null || total == 0) ? AuthConstants.ROLE_ADMIN : AuthConstants.ROLE_USER;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));

        // 用户不存在和密码错误返回同一个错误码：
        // 如果分开提示，攻击者可以拿它做用户名枚举
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功：id={}, username={}", user.getId(), user.getUsername());

        return new LoginVO(token, jwtUtil.getExpireSeconds(), toVO(user));
    }

    @Override
    public UserVO getCurrentUser() {
        CurrentUser current = UserContext.require();
        return getById(current.id());
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setSolvedCount(user.getSolvedCount());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
