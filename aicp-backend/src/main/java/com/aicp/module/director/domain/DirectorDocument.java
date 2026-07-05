package com.aicp.module.director.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 导演台文档协议 v1。
 * 坐标系统：RH_Y_UP_METERS。旋转：归一化 Quaternion。
 * 时间区间：半开 [0, duration_ms)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DirectorDocument(
        String coordinateSystem,   // "RH_Y_UP_METERS"
        int durationMs,
        int fps,
        List<CameraDefinition> cameras,     // 1–8 个相机
        String activeCameraId,
        List<SceneObject> objects,
        Timeline timeline
) {
    public DirectorDocument {
        if (coordinateSystem == null) coordinateSystem = "RH_Y_UP_METERS";
        if (cameras == null) cameras = List.of();
        if (objects == null) objects = List.of();
    }

    public record CameraDefinition(String id, String name,
                                    double focalLengthMm, double sensorWidthMm,
                                    double aperture, double nearClip, double farClip,
                                    String aspectRatioOverride) {}

    public record Quaternion(double x, double y, double z, double w) {
        public boolean isNormalized() {
            double norm = Math.sqrt(x*x + y*y + z*z + w*w);
            return Math.abs(norm - 1.0) < 1e-6;
        }
    }

    public record Vector3(double x, double y, double z) {}

    public record TimedTransform(int timeMs, Vector3 position, Quaternion rotation, Vector3 scale) {
        public TimedTransform {
            if (scale == null) scale = new Vector3(1, 1, 1);
        }
    }

    public record ActionClip(String clipKey, int inMs, int outMs, double weight) {}

    public record SceneObject(String id, String name, String type, String subType,
                               List<TimedTransform> keyframes, List<ActionClip> actions,
                               Vector3 position, Quaternion rotation, Vector3 scale,
                               String assetId, String groupId, boolean hidden) {}

    public record TimelineTrack(String trackId, String trackType, List<TimedKeyframe> keyframes) {}

    public record TimedKeyframe(int timeMs, String property, Object value, String interpolation) {}

    public record Timeline(List<TimelineTrack> tracks) {}
}
