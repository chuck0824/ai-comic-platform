package com.aicp.module.contentproject.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aicp.script")
public class ScriptStageTruthProperties {

    /**
     * R2-A 阶段事实链开关。关闭时阶段完成仍走旧轨推断；检查点可写可读供迁移预览，
     * 但 POST stage-transitions / fork / staleness 拒绝写入流转。
     */
    private boolean stageTruthEnabled = false;
}
