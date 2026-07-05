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
    WORKSPACE_UPSTREAM_UNAVAILABLE(41012, "账户中心暂不可用，请稍后重试"),

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
    LISTING_NOT_AVAILABLE(44007, "上架Listing不可用"),
    ORDER_STATE_CONFLICT(44008, "订单状态冲突，当前状态不允许此操作"),
    LICENSE_OPTION_NOT_AVAILABLE(44009, "授权方案不可用"),
    EXCLUSIVE_LICENSE_RESERVED(44010, "独家/买断授权已被保留"),
    EXCLUSIVE_LICENSE_SOLD(44011, "独家/买断授权已售出"),
    PURCHASE_APPROVAL_REQUIRED(44012, "企业采购需要审批"),
    WORKSPACE_WALLET_REQUIRED(44013, "企业订单必须使用企业钱包"),
    PAYMENT_RESULT_UNKNOWN(44014, "支付结果未知，请稍后查询"),
    DELIVERY_COMPENSATING(44015, "交付补偿中"),
    REFUND_NOT_ALLOWED(44016, "当前状态不允许退款"),
    INSUFFICIENT_BALANCE(44017, "余额不足"),

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
    STORYBOARD_JOB_CONFLICT(45011, "同类分镜任务正在运行"),

    // SOP 生产准入 72xxx
    SOP_RUN_NOT_FOUND(72001, "检查记录不存在"),
    SOP_RUN_STALE(72002, "检查报告已过期，请重新执行检查"),
    SOP_GATE_BLOCKED(72003, "生产准入 Gate 未通过"),
    SOP_WORK_ORDER_CONFLICT(72004, "工单操作冲突"),
    SOP_INVALID_TRANSITION(72005, "工单状态转换不允许"),
    SOP_MODULE_DISABLED(72006, "SOP 模块未启用"),

    // 资产市场 48xxx
    ASSET_NOT_FOUND(48001, "资产不存在"),
    ASSET_PERMISSION_DENIED(48002, "无资产操作权限"),
    LISTING_UNAVAILABLE(48003, "资产已下架或不可用"),
    ASSET_VERSION_CONFLICT(48004, "资产版本冲突，请刷新后重试"),
    ASSET_INCOMPATIBLE(48005, "资产与目标不兼容"),
    PUBLISH_STATE_CONFLICT(48006, "发布状态冲突，当前状态不允许此操作"),
    ASSET_TYPE_UNSUPPORTED(48007, "不支持的资产类型"),
    ASSET_FILE_MISSING(48008, "资产文件缺失"),
    ASSET_LIFECYCLE_CONFLICT(48009, "资产生命周期状态冲突"),
    ASSET_CATEGORY_INVALID(48010, "资产分类无效"),
    ASSET_PURGED(48011, "资产已清理不可恢复"),
    ASSET_BATCH_LIMIT(48012, "批量操作数量超限"),
    ASSET_IDEMPOTENCY_CONFLICT(48013, "幂等键冲突"),
    ASSET_CANVAS_TARGET_INVALID(48014, "目标画布无效"),
    ASSET_DOWNLOAD_SIGN_FAILED(48015, "下载签名失败"),
    ASSET_SETTLEMENT_FAILED(48016, "资产结算失败"),
    ASSET_COMPENSATION_EXHAUSTED(48017, "资产补偿已耗尽"),
    GENERATION_TASK_NOT_FOUND(46020, "生成任务不存在"),
    GENERATION_TASK_STATE_CONFLICT(46021, "生成任务状态冲突"),

    // Agent 配置中心 49xxx
    AGENT_BLUEPRINT_NOT_FOUND(49020, "Agent基础框架不存在"),
    AGENT_DEFINITION_NOT_FOUND(49021, "Agent定义不存在"),
    AGENT_VERSION_NOT_FOUND(49022, "Agent版本不存在"),
    AGENT_CONFIG_ACCESS_DENIED(49023, "无Agent配置权限"),
    AGENT_CONFIG_INVALID(49024, "Agent配置校验失败"),
    AGENT_VERSION_STATE_CONFLICT(49025, "Agent版本状态冲突"),
    AGENT_BINDING_CONFLICT(49026, "Agent绑定版本冲突"),
    AGENT_TEST_RUN_REQUIRED(49027, "发布前必须完成成功试跑");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
