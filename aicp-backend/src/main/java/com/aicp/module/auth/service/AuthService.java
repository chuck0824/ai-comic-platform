package com.aicp.module.auth.service;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.JwtUtil;
import com.aicp.common.util.RedisUtil;
import com.aicp.module.auth.dto.*;
import com.aicp.module.auth.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aicp.module.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService extends ServiceImpl<UserMapper, User> {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ===== 验证码 =====
    public void sendCode(String target, String type, String scene) {
        String code = String.valueOf(new Random().nextInt(900000) + 100000);
        String key = "code:" + scene + ":" + target;
        redisUtil.set(key, code, 5, TimeUnit.MINUTES);

        String retryKey = "code:retry:" + target;
        if (redisUtil.hasKey(retryKey)) {
            throw new BizException(ErrorCode.RATE_LIMIT, "验证码发送过于频繁，请60秒后重试");
        }
        redisUtil.set(retryKey, "1", 60, TimeUnit.SECONDS);
        log.info("验证码发送: target={}, type={}, scene={}, code={}", target, type, scene, code);
    }

    // ===== 注册 =====
    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        // 验证码校验
        String codeKey = "code:register:" + req.getAccount();
        String savedCode = redisUtil.get(codeKey, String.class);
        if (savedCode == null || !savedCode.equals(req.getVerifyCode())) {
            throw new BizException(ErrorCode.VERIFY_CODE_ERROR);
        }
        redisUtil.delete(codeKey);

        // 账号唯一性
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if ("phone".equals(req.getAccountType())) {
            qw.eq(User::getPhone, req.getAccount());
        } else {
            qw.eq(User::getEmail, req.getAccount());
        }
        if (userMapper.selectCount(qw) > 0) {
            throw new BizException(ErrorCode.ACCOUNT_EXISTS);
        }

        // 创建用户
        User user = new User();
        user.setUuid("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setNickname(req.getNickname());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setAccountType("personal");
        user.setMemberLevel("free");
        user.setStatus("active");
        user.setRealNameStatus("unverified");

        if ("phone".equals(req.getAccountType())) {
            user.setPhone(req.getAccount());
        } else {
            user.setEmail(req.getAccount());
        }
        userMapper.insert(user);

        // 生成 Token
        return buildLoginResult(user);
    }

    // ===== 密码登录 =====
    public Map<String, Object> loginByPassword(String account, String accountType) {
        return null; // placeholder for actual login
    }

    public Map<String, Object> login(String account, String accountType, String password) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if ("phone".equals(accountType)) {
            qw.eq(User::getPhone, account);
        } else {
            qw.eq(User::getEmail, account);
        }
        User user = userMapper.selectOne(qw);
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 记录失败次数
            String failKey = "login:fail:" + account;
            String failCount = redisUtil.get(failKey, String.class);
            int count = failCount != null ? Integer.parseInt(failCount) + 1 : 1;
            redisUtil.set(failKey, String.valueOf(count), 15, TimeUnit.MINUTES);
            if (count >= 5) {
                throw new BizException(ErrorCode.LOGIN_TOO_MANY);
            }
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 更新登录信息
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 清除失败计数
        redisUtil.delete("login:fail:" + account);

        return buildLoginResult(user);
    }

    // ===== 短信登录 =====
    public Map<String, Object> loginBySms(String phone, String verifyCode) {
        String codeKey = "code:login:" + phone;
        String savedCode = redisUtil.get(codeKey, String.class);
        if (savedCode == null || !savedCode.equals(verifyCode)) {
            throw new BizException(ErrorCode.VERIFY_CODE_ERROR);
        }
        redisUtil.delete(codeKey);

        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getPhone, phone);
        User user = userMapper.selectOne(qw);
        if (user == null) {
            // 自动注册
            user = new User();
            user.setUuid("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setAccountType("personal");
            user.setMemberLevel("free");
            user.setStatus("active");
            user.setRealNameStatus("unverified");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            userMapper.insert(user);
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        return buildLoginResult(user);
    }

    // ===== 微信登录 =====
    public Map<String, Object> loginByWechat(String code, String state) {
        // 实际环境中调用微信API换取openid
        String mockOpenid = "wx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getWechatOpenid, mockOpenid);
        User user = userMapper.selectOne(qw);

        if (user == null) {
            user = new User();
            user.setUuid("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            user.setWechatOpenid(mockOpenid);
            user.setNickname("微信用户");
            user.setAccountType("personal");
            user.setMemberLevel("free");
            user.setStatus("active");
            user.setRealNameStatus("unverified");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            userMapper.insert(user);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        return buildLoginResult(user);
    }

    // ===== 刷新Token =====
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            String userIdStr = jwtUtil.parseToken(refreshToken).getSubject();
            Long userId = Long.parseLong(userIdStr);
            String storedToken = redisUtil.getRefreshToken(userId);
            if (storedToken == null || !storedToken.equals(refreshToken)) {
                throw new BizException(ErrorCode.TOKEN_INVALID);
            }
            User user = userMapper.selectById(userId);
            if (user == null || "disabled".equals(user.getStatus())) {
                throw new BizException(ErrorCode.ACCOUNT_DISABLED);
            }
            redisUtil.deleteRefreshToken(userId);

            String newAccessToken = jwtUtil.generateAccessToken(
                    user.getId(), user.getUuid(), user.getAccountType(), null, null);
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
            redisUtil.setRefreshToken(user.getId(), newRefreshToken);

            return Map.of(
                    "access_token", newAccessToken,
                    "refresh_token", newRefreshToken,
                    "expires_in", 7200
            );
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    // ===== [DEV] 初始化测试账号 =====
    @Transactional
    public Map<String, Object> devInit(String account, String password) {
        // 先检查账号是否已存在
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, account));
        if (existing != null) {
            // 账号已存在，直接返回 token
            return buildLoginResult(existing);
        }

        User user = new User();
        user.setUuid("dev-" + UUID.randomUUID().toString().substring(0, 8));
        user.setAccountType("free_user");
        user.setPhone(account);
        user.setNickname("开发者");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMemberLevel("creator");
        user.setStatus("active");
        user.setRealNameStatus("verified");
        user.setAvatarUrl("");
        userMapper.insert(user);

        return buildLoginResult(user);
    }

    // ===== 登出 =====
    public void logout(String accessToken, String refreshToken) {
        try {
            Long userId = jwtUtil.getUserId(accessToken);
            redisUtil.blacklistToken(accessToken, 7200);
            redisUtil.deleteRefreshToken(userId);
        } catch (Exception ignored) {
        }
    }

    // ===== 构造登录结果 =====
    private Map<String, Object> buildLoginResult(User user) {
        List<String> permissions = new ArrayList<>();
        if ("creator".equals(user.getMemberLevel()) || "enterprise".equals(user.getMemberLevel())) {
            permissions.addAll(List.of(
                    "can_generate_script", "can_purchase_script", "can_generate_video",
                    "can_export_no_watermark", "can_manage_assets"
            ));
        } else {
            permissions.add("can_generate_script");
            permissions.add("can_purchase_script");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUuid(), user.getAccountType(), null, permissions);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        redisUtil.setRefreshToken(user.getId(), refreshToken);

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("uuid", user.getUuid());
        userData.put("nickname", user.getNickname());
        userData.put("account_type", user.getAccountType());
        userData.put("member_level", user.getMemberLevel());
        userData.put("avatar_url", user.getAvatarUrl());

        Map<String, Object> token = new LinkedHashMap<>();
        token.put("access_token", accessToken);
        token.put("refresh_token", refreshToken);
        token.put("expires_in", 7200);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userData);
        result.put("token", token);
        return result;
    }
}
