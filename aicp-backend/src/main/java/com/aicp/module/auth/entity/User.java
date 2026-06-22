package com.aicp.module.auth.entity;

import com.aicp.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {
    private String uuid;
    private String phone;
    private String email;
    private String wechatOpenid;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String accountType;
    private String realNameStatus;
    private String memberLevel;
    private LocalDateTime memberExpireAt;
    private String status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
}
