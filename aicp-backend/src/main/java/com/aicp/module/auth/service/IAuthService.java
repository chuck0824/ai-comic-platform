package com.aicp.module.auth.service;

import com.aicp.module.auth.dto.*;
import java.util.Map;

/**
 * 认证服务接口 — 定义认证模块对外契约。
 * 实现类：{@link AuthService}
 */
public interface IAuthService {

    /** 发送短信/邮箱验证码 */
    void sendCode(String target, String type, String scene);

    /** 注册新用户 */
    Map<String, Object> register(RegisterRequest req);

    /** 账号密码登录 */
    Map<String, Object> login(String account, String accountType, String password);

    /** 短信验证码登录 */
    Map<String, Object> loginBySms(String phone, String verifyCode);

    /** 微信登录 */
    Map<String, Object> loginByWechat(String code, String state);

    /** 刷新 Token */
    Map<String, Object> refreshToken(String refreshToken);

    /** 登出 */
    void logout(String accessToken, String refreshToken);
}
