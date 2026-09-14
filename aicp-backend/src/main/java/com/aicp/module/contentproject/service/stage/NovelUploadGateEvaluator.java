package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.UploadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NovelUploadGateEvaluator implements StageGateEvaluator {

    private static final Set<String> ACCEPTABLE_PARSE = Set.of("completed", "confirmed", "success");

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.NOVEL_UPLOAD;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        ContentUnit unit = support.findUnit(project.getId(), ScriptStageKey.NOVEL_UPLOAD.value());
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.NOVEL_UPLOAD.value());
        StageGateSupport.Builder b = support.builder()
                .evidence("contentUnitId", unit == null ? null : unit.getId())
                .evidence("source", payload.get("source"));

        b.require(unit != null, "CONTENT_UNIT_MISSING", "小说上传内容单元缺失");
        if (unit == null) {
            return b.build();
        }

        Object source = support.firstNonBlank(payload, "source");
        Object uploadId = support.firstNonBlank(payload, "uploadId", "upload_id", "sourceFileRef", "source_file_ref");
        Object pasted = support.firstNonBlank(payload, "pastedText", "pasted_text", "text", "content");
        b.require(source != null || uploadId != null || pasted != null,
                "SOURCE_FILE_REQUIRED", "上传文件引用或粘贴正文不能为空");

        if (uploadId != null) {
            Long id;
            try {
                id = Long.valueOf(String.valueOf(uploadId));
            } catch (Exception e) {
                id = null;
            }
            UploadFile upload = support.loadUpload(id);
            b.evidence("uploadId", id);
            b.evidence("parseStatus", upload == null ? null : upload.getParseStatus());
            b.require(upload != null, "SOURCE_FILE_REQUIRED", "上传文件引用无效");
            if (upload != null) {
                String status = upload.getParseStatus() == null ? "" : upload.getParseStatus().toLowerCase(Locale.ROOT);
                // 粘贴路径可能没有 upload；文件路径要求解析完成（completed≈CONFIRMED）
                boolean ok = ACCEPTABLE_PARSE.contains(status)
                        || (upload.getParsedText() != null && !upload.getParsedText().isBlank());
                b.require(ok, "PARSE_NOT_CONFIRMED", "小说解析尚未确认完成");
            }
        } else if ("paste".equalsIgnoreCase(String.valueOf(source))) {
            b.require(!support.blank(pasted), "SOURCE_FILE_REQUIRED", "粘贴正文不能为空");
        }
        return b.build();
    }
}
