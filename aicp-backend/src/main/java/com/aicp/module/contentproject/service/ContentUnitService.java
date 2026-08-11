package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentUnitService {

    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;

    // ===== Create Unit =====

    @Transactional
    public ContentUnitView createUnit(Long userId, Long projectId, String unitType, int displayNo, String title) {
        // check for duplicate display order
        long count = unitMapper.selectCount(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, unitType)
                        .eq(ContentUnit::getDisplayNo, displayNo));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该显示序号已存在");
        }

        ContentUnit unit = new ContentUnit();
        unit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
        unit.setProjectId(projectId);
        unit.setUnitType(unitType);
        unit.setDisplayNo(displayNo);
        unit.setTitle(title);
        unit.setStatus("draft");
        unit.setRevision(0);
        unit.setIsDeleted(0);
        unitMapper.insert(unit);

        return new ContentUnitView(unit.getId(), unit.getStableKey(), unit.getUnitType(),
                unit.getDisplayNo(), unit.getTitle(), unit.getStatus(),
                unit.getCurrentVersionId(), unit.getRevision(),
                unit.getCreatedAt(), unit.getUpdatedAt());
    }

    // ===== List Units =====

    public List<ContentUnitView> listUnits(Long projectId) {
        return unitMapper.selectList(
                        new LambdaQueryWrapper<ContentUnit>()
                                .eq(ContentUnit::getProjectId, projectId)
                                .eq(ContentUnit::getIsDeleted, 0)
                                .orderByAsc(ContentUnit::getDisplayNo))
                .stream()
                .map(u -> new ContentUnitView(u.getId(), u.getStableKey(), u.getUnitType(),
                        u.getDisplayNo(), u.getTitle(), u.getStatus(),
                        u.getCurrentVersionId(), u.getRevision(),
                        u.getCreatedAt(), u.getUpdatedAt()))
                .toList();
    }

    // ===== Save Draft (autosave with revision conflict) =====

    @Transactional
    public DraftView saveDraft(Long userId, Long unitId, SaveDraftRequest request) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null || unit.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // optimistic lock
        if (!request.revision().equals(unit.getRevision())) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        int revision = unit.getRevision() == null ? 0 : unit.getRevision();
        int claimed = unitMapper.update(null, new UpdateWrapper<ContentUnit>()
                .eq("id", unitId)
                .eq("revision", revision)
                .set("revision", revision + 1));
        if (claimed == 0) throw new BizException(ErrorCode.EDIT_CONFLICT);

        String hash = sha256(request.contentJson() != null ? request.contentJson() : "");

        // find existing draft version
        ContentVersion draft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "draft")
                        .eq(ContentVersion::getSource, "manual_edit"));

        if (draft != null) {
            draft.setContentJson(request.contentJson());
            draft.setPlainText(request.plainText());
            draft.setContentHash(hash);
            versionMapper.updateById(draft);
        } else {
            draft = new ContentVersion();
            draft.setProjectId(unit.getProjectId());
            draft.setContentUnitId(unitId);
            draft.setVersionNo(0); // draft is not a numbered version
            draft.setStatus("draft");
            draft.setContentJson(request.contentJson());
            draft.setPlainText(request.plainText());
            draft.setSource("manual_edit");
            draft.setContentHash(hash);
            draft.setCreatedBy(userId);
            versionMapper.insert(draft);
        }

        // increment unit revision
        unit.setRevision(revision + 1);

        return new DraftView(draft.getId(), unitId, unit.getRevision(),
                draft.getContentJson(), draft.getPlainText(), draft.getCreatedAt());
    }

    // ===== Get Draft =====

    public DraftView getDraft(Long userId, Long unitId) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null || unit.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        ContentVersion draft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "draft"));

        if (draft == null) {
            return new DraftView(null, unitId, unit.getRevision(), "{}", "", null);
        }
        return new DraftView(draft.getId(), unitId, unit.getRevision(),
                draft.getContentJson(), draft.getPlainText(), draft.getCreatedAt());
    }

    // ===== Create Named Version =====

    @Transactional
    public ContentVersionView createVersion(Long userId, Long unitId, CreateVersionRequest request) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null || unit.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // get current draft as source
        ContentVersion draft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "draft"));

        String contentJson = draft != null ? draft.getContentJson() : "{}";
        String plainText = draft != null ? draft.getPlainText() : "";
        String hash = sha256(contentJson);

        // get next version_no
        List<ContentVersion> existing = versionMapper.selectList(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .orderByDesc(ContentVersion::getVersionNo)
                        .last("limit 1"));
        int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersionNo() + 1;

        ContentVersion version = new ContentVersion();
        version.setProjectId(unit.getProjectId());
        version.setContentUnitId(unitId);
        version.setVersionNo(nextVersion);
        version.setStatus(request.status());
        version.setContentJson(contentJson);
        version.setPlainText(plainText);
        version.setSource("manual_edit");
        version.setContentHash(hash);
        version.setCreatedBy(userId);
        versionMapper.insert(version);

        // update unit's current version pointer
        unit.setCurrentVersionId(version.getId());
        unitMapper.updateById(unit);

        return new ContentVersionView(version.getId(), version.getVersionNo(),
                version.getStatus(), version.getContentJson(), version.getPlainText(),
                version.getSource(), version.getContentHash(), version.getCreatedBy(),
                version.getCreatedAt());
    }

    // ===== List Versions =====

    public List<ContentVersionView> listVersions(Long unitId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .gt(ContentVersion::getVersionNo, 0)
                        .notIn(ContentVersion::getStatus, "candidate", "discarded")
                        .orderByDesc(ContentVersion::getVersionNo))
                .stream()
                .filter(v -> !"candidate".equals(v.getStatus()) && !"discarded".equals(v.getStatus()))
                .map(v -> new ContentVersionView(v.getId(), v.getVersionNo(),
                        v.getStatus(), v.getContentJson(), v.getPlainText(),
                        v.getSource(), v.getContentHash(), v.getCreatedBy(),
                        v.getCreatedAt()))
                .toList();
    }

    // ===== Restore Version =====

    @Transactional
    public DraftView restoreVersion(Long userId, Long unitId, Long versionId) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null || unit.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        ContentVersion source = versionMapper.selectById(versionId);
        if (source == null || !source.getContentUnitId().equals(unitId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if ("candidate".equals(source.getStatus()) || "discarded".equals(source.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未采用或已丢弃的生成版本不能恢复");
        }

        // create a new draft from the selected version
        ContentVersion existingDraft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "draft"));

        if (existingDraft != null) {
            existingDraft.setContentJson(source.getContentJson());
            existingDraft.setPlainText(source.getPlainText());
            existingDraft.setContentHash(source.getContentHash());
            versionMapper.updateById(existingDraft);
        } else {
            ContentVersion newDraft = new ContentVersion();
            newDraft.setProjectId(unit.getProjectId());
            newDraft.setContentUnitId(unitId);
            newDraft.setVersionNo(0);
            newDraft.setStatus("draft");
            newDraft.setContentJson(source.getContentJson());
            newDraft.setPlainText(source.getPlainText());
            newDraft.setSource("manual_edit");
            newDraft.setContentHash(source.getContentHash());
            newDraft.setCreatedBy(userId);
            versionMapper.insert(newDraft);
        }

        unit.setRevision(unit.getRevision() + 1);
        unitMapper.updateById(unit);

        return getDraft(userId, unitId);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
