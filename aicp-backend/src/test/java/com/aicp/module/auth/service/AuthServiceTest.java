package com.aicp.module.auth.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.JwtUtil;
import com.aicp.common.util.RedisUtil;
import com.aicp.module.auth.dto.RegisterRequest;
import com.aicp.module.auth.entity.User;
import com.aicp.module.auth.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private RedisUtil redisUtil;
    @Mock private Environment environment;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest();
        validRequest.setAccount("13800000001");
        validRequest.setAccountType("phone");
        validRequest.setPassword("Abc12345");
        validRequest.setVerifyCode("123456");
        validRequest.setAccountCategory("personal");
        validRequest.setNickname("测试用户");
    }

    @Test
    @DisplayName("短信验证码过期 → 抛出 BizException(VERIFY_CODE_ERROR)")
    void registerWithExpiredCode() {
        when(redisUtil.get("code:register:13800000001", String.class)).thenReturn(null);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VERIFY_CODE_ERROR.getCode());
    }

    @Test
    @DisplayName("账号已存在 → 抛出 BizException(ACCOUNT_EXISTS)")
    void registerWithExistingAccount() {
        when(redisUtil.get("code:register:13800000001", String.class)).thenReturn("123456");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ACCOUNT_EXISTS.getCode());
    }

    @Test
    @DisplayName("验证码正确且账号不存在 → 注册成功，返回 token")
    void registerSuccess() {
        when(redisUtil.get("code:register:13800000001", String.class)).thenReturn("123456");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("Abc12345")).thenReturn("$2a$10$hashedPassword");
        when(userMapper.insert(any(User.class))).thenReturn(1);
        // JWT claims mock — use lenient matchers since userId is set by MyBatis-Plus after insert
        when(jwtUtil.generateAccessToken(any(), anyString(), anyString(), any(), any())).thenReturn("fake-jwt-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("fake-refresh-token");

        Map<String, Object> result = authService.register(validRequest);

        assertThat(result).containsKeys("user", "token");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = (Map<String, Object>) result.get("token");
        assertThat(token).containsKeys("access_token", "refresh_token");
        assertThat(token.get("access_token")).isEqualTo("fake-jwt-token");
        verify(userMapper).insert(any(User.class));
        verify(redisUtil).delete("code:register:13800000001");
    }

    @Test
    @DisplayName("devInit 在非 dev 环境 → 抛出 BizException(FORBIDDEN)")
    void devInitBlockedOutsideDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        assertThatThrownBy(() -> authService.devInit("admin", "admin123"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }
}
