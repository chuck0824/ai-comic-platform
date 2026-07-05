package com.aicp.module.canvas.service;

import com.aicp.module.canvas.domain.CanvasKernelEnums.PortDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 类型化端口注册表 v1。
 * 定义 14 种端口 payload 类型的输入/输出方向和允许的角色。
 * 服务端权威校验；前端镜像仅用于即时拖拽反馈。
 */
@Slf4j
@Component
public class CanvasPortRegistry {

    public static final String CONTRACT_VERSION = "canvas-ports-v1";

    public record PortDefinition(String key, String payloadType, PortDirection direction, Set<String> allowedRoles) {}

    private final Map<String, PortDefinition> definitions = new LinkedHashMap<>();

    public CanvasPortRegistry() {
        // OUTPUT ports (source side)
        register("text_out", "text", PortDirection.OUTPUT, Set.of("prompt", "dialogue", "description"));
        register("shot", "shot", PortDirection.OUTPUT, Set.of("shot_identity"));
        register("image_ref", "image_ref", PortDirection.OUTPUT, Set.of("identity", "scene", "prop", "character"));
        register("motion_ref", "motion_ref", PortDirection.OUTPUT, Set.of("motion_source"));
        register("camera_ref", "camera_ref", PortDirection.OUTPUT, Set.of("camera_source"));
        register("audio_ref", "audio_ref", PortDirection.OUTPUT, Set.of("audio_source"));
        register("director_package", "director_package", PortDirection.OUTPUT, Set.of("director_output"));
        register("video_candidate", "video_candidate", PortDirection.OUTPUT, Set.of("candidate_output"));
        register("quality_report", "quality_report", PortDirection.OUTPUT, Set.of("quality_output"));

        // INPUT ports (target side)
        register("image_ref", "image_ref", PortDirection.INPUT, Set.of("identity", "scene", "composition", "style_ref"));
        register("motion_ref", "motion_ref", PortDirection.INPUT, Set.of("motion_reference"));
        register("camera_ref", "camera_ref", PortDirection.INPUT, Set.of("camera_reference"));
        register("audio_ref", "audio_ref", PortDirection.INPUT, Set.of("audio_timing", "audio_reference"));
        register("director_package", "director_package", PortDirection.INPUT, Set.of("director_input"));
    }

    private void register(String key, String payloadType, PortDirection direction, Set<String> roles) {
        String mapKey = key + ":" + direction;
        definitions.put(mapKey, new PortDefinition(key, payloadType, direction, roles));
    }

    /**
     * 校验两个端口是否可以连接。
     *
     * @param sourceType    源节点类型
     * @param sourcePort    源端口 key
     * @param targetType    目标节点类型
     * @param targetPort    目标端口 key
     * @param role          端口角色（输入侧），可为 null
     * @return 连接决策
     */
    public ConnectionDecision validate(String sourceType, String sourcePort,
                                       String targetType, String targetPort, String role) {
        PortDefinition sourceDef = definitions.get(sourcePort + ":" + PortDirection.OUTPUT);
        PortDefinition targetDef = definitions.get(targetPort + ":" + PortDirection.INPUT);

        if (sourceDef == null) {
            return new ConnectionDecision(false, CONTRACT_VERSION, "未知源端口: " + sourcePort);
        }
        if (targetDef == null) {
            return new ConnectionDecision(false, CONTRACT_VERSION, "未知目标端口: " + targetPort);
        }
        if (!sourceDef.payloadType.equals(targetDef.payloadType)) {
            return new ConnectionDecision(false, CONTRACT_VERSION,
                    String.format("端口类型不匹配: %s → %s", sourceDef.payloadType, targetDef.payloadType));
        }
        if (role != null && !targetDef.allowedRoles.contains(role)) {
            return new ConnectionDecision(false, CONTRACT_VERSION,
                    String.format("角色 %s 不允许用于端口 %s，允许: %s", role, targetPort, targetDef.allowedRoles));
        }
        return new ConnectionDecision(true, CONTRACT_VERSION, "OK");
    }

    /**
     * 检查指定节点端口是否可以连接（简化版，忽略 role）。
     */
    public boolean canConnect(String sourceType, String sourcePort, String targetType, String targetPort, String role) {
        return validate(sourceType, sourcePort, targetType, targetPort, role).allowed();
    }

    public PortDefinition getDefinition(String portKey, PortDirection direction) {
        return definitions.get(portKey + ":" + direction);
    }

    public Set<String> allPortKeys() {
        Set<String> keys = new LinkedHashSet<>();
        definitions.values().forEach(d -> keys.add(d.key));
        return Collections.unmodifiableSet(keys);
    }

    public record ConnectionDecision(boolean allowed, String contractVersion, String reason) {}
}
