package com.aicp.module.contentproject.service.stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StageGateResult(
        List<Map<String, Object>> blockers,
        List<Map<String, Object>> warnings,
        Map<String, Object> evidence
) {
    public static StageGateResult pass(Map<String, Object> evidence) {
        return new StageGateResult(List.of(), List.of(), evidence == null ? Map.of() : evidence);
    }

    public static StageGateResult of(
            List<Map<String, Object>> blockers,
            List<Map<String, Object>> warnings,
            Map<String, Object> evidence) {
        Map<String, Object> evidenceCopy = new LinkedHashMap<>();
        if (evidence != null) {
            for (Map.Entry<String, Object> entry : evidence.entrySet()) {
                if (entry.getKey() != null) {
                    evidenceCopy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return new StageGateResult(
                blockers == null ? List.of() : List.copyOf(blockers),
                warnings == null ? List.of() : List.copyOf(warnings),
                java.util.Collections.unmodifiableMap(evidenceCopy));
    }

    public boolean blocked() {
        return blockers != null && !blockers.isEmpty();
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("blockers", blockers);
        map.put("warnings", warnings);
        map.put("evidence", evidence);
        return map;
    }

    public static Map<String, Object> issue(String code, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("message", message);
        return item;
    }

    public StageGateResult withBlocker(String code, String message) {
        List<Map<String, Object>> next = new ArrayList<>(blockers);
        next.add(issue(code, message));
        return of(next, warnings, evidence);
    }
}
