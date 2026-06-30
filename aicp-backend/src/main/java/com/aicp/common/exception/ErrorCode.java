package com.aicp.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "success"),

    // 通用错误 4xxxx
    RATE_LIMIT(40001, "请求频率超限"),
    PARAM_INVALID(40002, "参数校验失败"),
    UNAUTHORIZED(40003, "未认证"),
    FORBIDDEN(40004, "无权限"),
    NOT_FOUND(40005, "资源不存在"),
    CONFLICT(40006, "资源冲突"),

    // 系统错误 5xxxx
    INTERNAL_ERROR(50001, "服务器内部错误"),
    SERVICE_UNAVAILABLE(50002, "服务不可用"),
    UPSTREAM_TIMEOUT(50003, "上游服务超时"),

    // 用户服务 41xxx
    ACCOUNT_EXISTS(41001, "账号已存在"),
    VERIFY_CODE_ERROR(41002, "验证码错误或过期"),
    PASSWORD_INVALID(41003, "密码格式不符"),
    LOGIN_FAILED(41004, "账号或密码错误"),
    ACCOUNT_DISABLED(41005, "账号已禁用"),
    LOGIN_TOO_MANY(41006, "登录失败次数过多，请15分钟后重试"),
    TOKEN_EXPIRED(41007, "Token过期"),
    TOKEN_INVALID(41008, "Token无效"),
    ENTERPRISE_NOT_VERIFIED(41009, "企业认证未通过"),
    MEMBER_LIMIT_EXCEEDED(41010, "成员数已达上限"),
    ENTERPRISE_BUDGET_INSUFFICIENT(41011, "企业预算不足"),

    // 剧本生成 42xxx
    GEN_QUOTA_EXHAUSTED(42001, "生成配额已用完"),
    AI_SERVICE_UNAVAILABLE(42002, "AI服务不可用"),
    GEN_TIMEOUT(42003, "生成任务超时"),
    CONTENT_ILLEGAL(42004, "输入内容不合规"),
    CONTENT_BLOCKED(42005, "生成内容被安全拦截"),
    TOKEN_BUDGET_INSUFFICIENT(42006, "Token预算不足"),

    // 交易服务 44xxx
    SCRIPT_DELISTED(44001, "剧本已下架"),
    ORDER_EXPIRED(44002, "订单已过期"),
    PAY_FAILED(44003, "支付失败"),
    ALREADY_PURCHASED(44004, "已购买过该剧本"),
    BALANCE_INSUFFICIENT(44005, "余额不足"),
    WITHDRAW_BELOW_MINIMUM(44006, "提现金额低于最低限额"),

    // 内容项目 43xxx
    PROJECT_NOT_FOUND(43001, "项目不存在"),
    PROJECT_ACCESS_DENIED(43002, "无项目访问权限"),
    EDIT_CONFLICT(43003, "编辑冲突，数据已被他人修改"),
    WORKFLOW_STAGE_LOCKED(43004, "工作流阶段已锁定"),
    ARTIFACT_LOCKED(43005, "产物已锁定"),
    DEPENDENCY_STALE(43006, "依赖已过期"),
    IDEMPOTENCY_CONFLICT(43007, "幂等键冲突"),

    // 画布服务 46xxx
    CANVAS_NOT_FOUND(46001, "画布项目不存在"),
    CANVAS_NODE_NOT_FOUND(46011, "画布节点不存在"),
    CANVAS_EDGE_NOT_FOUND(46012, "连线节点不存在"),
    SHOTS_INCOMPLETE(46002, "分镜未完成，无法合成"),
    RENDER_FAILED(46003, "渲染失败"),
    EXPORT_QUEUE_FULL(46004, "导出队列已满"),
    EXPORT_NO_WATERMARK_DENIED(46005, "无水印导出需要会员"),

    // SOP服务 47xxx
    SOP_CHECK_FAILED(47001, "生产准入未通过"),
    ASSET_LOCKED(47002, "资产已锁定，无法修改"),
    VERSION_CONFLICT(47003, "版本冲突"),
    AI_FAILURE_LIMIT(47004, "AI失败次数超限"),

    // 分镜专业编辑器 45xxx
    STORYBOARD_NOT_FOUND(45001, "分镜不存在"),
    STORYBOARD_VERSION_NOT_FOUND(45002, "分镜版本不存在"),
    STORYBOARD_VERSION_LOCKED(45003, "分镜版本已锁定"),
    STORYBOARD_REVISION_CONFLICT(45004, "分镜版本已被他人修改"),
    SOURCE_CONTENT_VERSION_STALE(45005, "源正文版本已更新"),
    INVALID_TIER_TRANSITION(45006, "分镜升档路径无效"),
    REVIEW_ISSUES_UNRESOLVED(45007, "仍有未处理的审核问题"),
    PRODUCTION_GATE_FAILED(45008, "生产准入未通过"),
    XLSX_TEMPLATE_UNSUPPORTED(45009, "不支持的分镜工作簿模板"),
    XLSX_VALIDATION_FAILED(45010, "分镜工作簿校验失败"),
    STORYBOARD_JOB_CONFLICT(45011, "同类分镜任务正在运行");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
