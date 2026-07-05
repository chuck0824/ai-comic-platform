package com.aicp.module.director.service;

import com.aicp.module.director.domain.DirectorDocument;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DirectorDocumentValidator {

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public static ValidationResult ok() { return new ValidationResult(true, List.of(), List.of()); }
        public static ValidationResult fail(List<String> errors) { return new ValidationResult(false, errors, List.of()); }
        public static ValidationResult warn(List<String> warnings) { return new ValidationResult(true, List.of(), warnings); }
    }

    public ValidationResult validate(DirectorDocument doc) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 坐标系统校验
        if (!"RH_Y_UP_METERS".equals(doc.coordinateSystem())) {
            errors.add("坐标系统必须为 RH_Y_UP_METERS");
        }

        // 时长与帧率
        if (doc.durationMs() <= 0) errors.add("时长必须 > 0");
        if (doc.fps() < 1 || doc.fps() > 120) errors.add("帧率必须在 1–120 之间");

        // 相机校验
        if (doc.cameras() == null || doc.cameras().isEmpty()) {
            errors.add("至少需要一个相机定义");
        } else if (doc.cameras().size() > 8) {
            errors.add("最多 8 个相机");
        }
        if (doc.activeCameraId() != null) {
            boolean found = doc.cameras().stream().anyMatch(c -> c.id().equals(doc.activeCameraId()));
            if (!found) errors.add("activeCameraId 不在 cameras 列表中");
        }

        // 对象校验
        Set<String> objIds = new HashSet<>();
        for (var obj : doc.objects()) {
            if (!objIds.add(obj.id())) errors.add("重复对象ID: " + obj.id());
            if (obj.name() == null || obj.name().isBlank()) errors.add("对象 " + obj.id() + " 缺少名称");
        }

        // Quaternion 归一化
        for (var obj : doc.objects()) {
            for (var kf : obj.keyframes()) {
                var q = kf.rotation();
                if (q != null && !q.isNormalized()) {
                    errors.add("对象 " + obj.id() + " 关键帧 " + kf.timeMs() + "ms 的 Quaternion 未归一化");
                }
            }
        }

        // 关键帧时间范围：必须在 [0, duration_ms) 内
        int durationMs = doc.durationMs();
        for (var obj : doc.objects()) {
            for (var kf : obj.keyframes()) {
                if (kf.timeMs() < 0 || kf.timeMs() >= durationMs) {
                    errors.add("对象 " + obj.id() + " 的关键帧 " + kf.timeMs() + "ms 超出 [0, " + durationMs + ")");
                }
            }
        }

        // Action clip 校验
        for (var obj : doc.objects()) {
            for (var action : obj.actions()) {
                if (action.outMs() <= action.inMs()) {
                    errors.add("对象 " + obj.id() + " 的动作片段 out_ms <= in_ms");
                }
                if (action.inMs() < 0 || action.outMs() > durationMs) {
                    errors.add("对象 " + obj.id() + " 的动作片段超出时间范围");
                }
            }
            // 重叠检测
            if (obj.actions().size() > 1) {
                var sorted = new ArrayList<>(obj.actions());
                sorted.sort(Comparator.comparingInt(DirectorDocument.ActionClip::inMs));
                for (int i = 1; i < sorted.size(); i++) {
                    if (sorted.get(i).inMs() < sorted.get(i - 1).outMs()) {
                        warnings.add("对象 " + obj.id() + " 存在动作重叠: [" + sorted.get(i - 1).inMs() + ", " + sorted.get(i - 1).outMs() + ") vs [" + sorted.get(i).inMs() + ", " + sorted.get(i).outMs() + ")");
                    }
                }
            }
        }

        // 空场景提示
        if (doc.objects().isEmpty()) {
            warnings.add("场景没有任何对象");
        }

        if (!errors.isEmpty()) return ValidationResult.fail(errors);
        if (!warnings.isEmpty()) return ValidationResult.warn(warnings);
        return ValidationResult.ok();
    }
}
