package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.CreativeBibleRequests.CreateBibleDraftRequest;
import com.aicp.module.contentproject.dto.CreativeBibleRequests.UpsertEcosystemRuleRequest;
import com.aicp.module.contentproject.dto.CreativeBibleRequests.UpsertWritingGuideRequest;
import com.aicp.module.contentproject.dto.CreativeBibleViews.BibleSummaryView;
import com.aicp.module.contentproject.dto.CreativeBibleViews.EcosystemRuleView;
import com.aicp.module.contentproject.dto.CreativeBibleViews.WritingGuideView;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeBibleService {

    private final CreativeBibleVersionMapper bibleMapper;
    private final EcosystemRuleMapper ecosystemMapper;
    private final ProjectWritingGuideMapper guideMapper;
    private final ProjectSettingEntityMapper settingMapper;
    private final ProjectSettingVersionMapper settingVersionMapper;
    private final ProjectAccessService accessService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    // ── Read ──

    public BibleSummaryView getCurrent(Long userId, Long projectId) {
        accessService.require(projectId, userId, Action.VIEW);
        CreativeBibleVersion latest = findLatest(projectId, null);
        return latest == null ? null : toSummary(latest);
    }

    public Map<String, Object> health(Long userId, Long projectId) {
        accessService.require(projectId, userId, Action.VIEW);
        CreativeBibleVersion current = findLatest(projectId, "confirmed");
        CreativeBibleVersion draft = findLatest(projectId, "draft");

        long confirmedFactCount = 0;
        if (current != null) {
            confirmedFactCount = ecosystemMapper.selectCount(
                    new LambdaQueryWrapper<EcosystemRule>()
                            .eq(EcosystemRule::getProjectId, projectId)
                            .eq(EcosystemRule::getBibleVersionId, current.getId())
                            .eq(EcosystemRule::getStatus, "confirmed"))
                    + settingMapper.selectCount(
                            new LambdaQueryWrapper<ProjectSettingEntity>()
                                    .eq(ProjectSettingEntity::getProjectId, projectId)
                                    .eq(ProjectSettingEntity::getStatus, "confirmed"));
        }

        long pendingCount = 0;
        if (draft != null && !draft.getId().equals(current != null ? current.getId() : null)) {
            pendingCount = ecosystemMapper.selectCount(
                    new LambdaQueryWrapper<EcosystemRule>()
                            .eq(EcosystemRule::getProjectId, projectId)
                            .eq(EcosystemRule::getBibleVersionId, draft.getId()))
                    + guideMapper.selectCount(
                            new LambdaQueryWrapper<ProjectWritingGuide>()
                                    .eq(ProjectWritingGuide::getProjectId, projectId)
                                    .eq(ProjectWritingGuide::getBibleVersionId, draft.getId()));
        }

        return Map.of(
                "status", current == null ? "missing" : current.getStatus(),
                "current_version_id", current == null ? 0L : current.getId(),
                "current_version_no", current == null ? 0 : current.getVersionNo(),
                "confirmed_fact_count", confirmedFactCount,
                "pending_change_count", pendingCount,
                "ready_for_generation", current != null && "confirmed".equals(current.getStatus())
        );
    }

    public Page<EcosystemRuleView> listEcosystem(Long userId, Long projectId, Long bibleVersionId,
                                                  String ruleType, int page, int pageSize) {
        accessService.require(projectId, userId, Action.VIEW);
        LambdaQueryWrapper<EcosystemRule> query = new LambdaQueryWrapper<>();
        query.eq(EcosystemRule::getProjectId, projectId);
        query.eq(EcosystemRule::getBibleVersionId, bibleVersionId);
        if (ruleType != null && !ruleType.isBlank()) {
            query.eq(EcosystemRule::getRuleType, ruleType);
        }
        query.orderByAsc(EcosystemRule::getRuleType, EcosystemRule::getId);
        Page<EcosystemRule> result = ecosystemMapper.selectPage(new Page<>(page, pageSize), query);
        Page<EcosystemRuleView> viewPage = new Page<>(page, pageSize, result.getTotal());
        viewPage.setRecords(result.getRecords().stream().map(this::toEcosystemView).toList());
        return viewPage;
    }

    // ── Write ──

    @Transactional
    public BibleSummaryView createDraft(Long userId, Long projectId, CreateBibleDraftRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        CreativeBibleVersion latest = findLatest(projectId, null);
        int nextVersion = (latest == null) ? 1 : latest.getVersionNo() + 1;

        CreativeBibleVersion draft = new CreativeBibleVersion();
        draft.setProjectId(projectId);
        draft.setVersionNo(nextVersion);
        draft.setStatus("draft");
        draft.setSummary(request.summary());
        draft.setSourceVersionId(request.sourceVersionId());
        draft.setSnapshotJson("{}");
        draft.setCreatedBy(userId);
        bibleMapper.insert(draft);

        // If source version specified, copy its ecosystem rules and confirmed guides
        if (request.sourceVersionId() != null) {
            copyFromSource(projectId, draft.getId(), request.sourceVersionId());
        }

        log.info("创作圣经草稿创建: projectId={}, versionNo={}, id={}", projectId, nextVersion, draft.getId());
        return toSummary(draft);
    }

    @Transactional
    public EcosystemRuleView upsertEcosystem(Long userId, Long projectId, Long bibleVersionId,
                                              Long ruleId, UpsertEcosystemRuleRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        CreativeBibleVersion bible = getBibleOrFail(bibleVersionId, projectId);
        if (!"draft".equals(bible.getStatus()) && !"reviewable".equals(bible.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已确认的创作圣经不可修改，请先创建新草稿");
        }

        EcosystemRule rule;
        if (ruleId != null) {
            rule = ecosystemMapper.selectById(ruleId);
            if (rule == null || !rule.getProjectId().equals(projectId)) {
                throw new BizException(ErrorCode.NOT_FOUND, "生态规则不存在: " + ruleId);
            }
            if (request.revision() != null && !request.revision().equals(rule.getRevision())) {
                throw new BizException(ErrorCode.EDIT_CONFLICT, "生态规则已被他人修改，请刷新后重试");
            }
        } else {
            rule = new EcosystemRule();
            rule.setProjectId(projectId);
            rule.setBibleVersionId(bibleVersionId);
            rule.setCreatedBy(userId);
        }

        rule.setRuleType(request.ruleType());
        rule.setName(request.name());
        rule.setSummary(request.summary());
        rule.setDetailsJson(toJson(request.details()));
        rule.setScopeJson(toJson(request.scope()));
        rule.setExceptionsJson(toJson(request.exceptions()));
        rule.setSourceType(request.sourceType() != null ? request.sourceType() : "manual");
        rule.setStatus("draft");
        rule.setRevision(rule.getRevision() != null ? rule.getRevision() + 1 : 1);
        rule.setUpdatedBy(userId);

        if (ruleId != null) {
            ecosystemMapper.updateById(rule);
        } else {
            ecosystemMapper.insert(rule);
        }

        return toEcosystemView(rule);
    }

    @Transactional
    public BibleSummaryView confirm(Long userId, Long projectId, Long bibleVersionId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        CreativeBibleVersion version = getBibleOrFail(bibleVersionId, projectId);

        if (!"draft".equals(version.getStatus()) && !"reviewable".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有草稿或待确认版本可以确认");
        }

        long factCount = ecosystemMapper.selectCount(
                new LambdaQueryWrapper<EcosystemRule>()
                        .eq(EcosystemRule::getProjectId, projectId)
                        .eq(EcosystemRule::getBibleVersionId, bibleVersionId))
                + settingMapper.selectCount(
                        new LambdaQueryWrapper<ProjectSettingEntity>()
                                .eq(ProjectSettingEntity::getProjectId, projectId)
                                .eq(ProjectSettingEntity::getStatus, "confirmed"));
        if (factCount == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少确认一项生态或实体设定");
        }

        // Build snapshot
        String snapshotJson = buildSnapshot(projectId, bibleVersionId);
        String snapshotHash = DigestUtils.md5DigestAsHex(snapshotJson.getBytes(StandardCharsets.UTF_8));

        version.setSnapshotJson(snapshotJson);
        version.setSnapshotHash(snapshotHash);
        version.setStatus("confirmed");
        version.setConfirmedBy(userId);
        version.setConfirmedAt(LocalDateTime.now());
        bibleMapper.updateById(version);

        // Supersede previous confirmed
        CreativeBibleVersion prevConfirmed = findLatestExcept(projectId, "confirmed", bibleVersionId);
        if (prevConfirmed != null) {
            prevConfirmed.setStatus("superseded");
            bibleMapper.updateById(prevConfirmed);
        }

        // Confirm ecosystem rules in this version
        ecosystemMapper.selectList(
                new LambdaQueryWrapper<EcosystemRule>()
                        .eq(EcosystemRule::getProjectId, projectId)
                        .eq(EcosystemRule::getBibleVersionId, bibleVersionId)
                        .eq(EcosystemRule::getStatus, "draft"))
                .forEach(r -> {
                    r.setStatus("confirmed");
                    ecosystemMapper.updateById(r);
                });

        // Confirm writing guides in this version
        guideMapper.selectList(
                new LambdaQueryWrapper<ProjectWritingGuide>()
                        .eq(ProjectWritingGuide::getProjectId, projectId)
                        .eq(ProjectWritingGuide::getBibleVersionId, bibleVersionId)
                        .eq(ProjectWritingGuide::getStatus, "draft"))
                .forEach(g -> {
                    g.setStatus("confirmed");
                    g.setConfirmedBy(userId);
                    g.setConfirmedAt(LocalDateTime.now());
                    guideMapper.updateById(g);
                });

        outboxService.append("CREATIVE_BIBLE_CONFIRMED", projectId, version.getVersionNo(),
                Map.of("project_id", projectId, "bible_version_id", bibleVersionId,
                        "version_no", version.getVersionNo(), "snapshot_hash", snapshotHash));

        log.info("创作圣经确认: projectId={}, versionNo={}, factCount={}", projectId, version.getVersionNo(), factCount);
        return toSummary(version);
    }

    @Transactional
    public BibleSummaryView ensureDraftForChange(Long userId, Long projectId, String reason) {
        CreativeBibleVersion existingDraft = findLatest(projectId, "draft");
        if (existingDraft != null) return toSummary(existingDraft);
        CreativeBibleVersion confirmed = findLatest(projectId, "confirmed");
        return createDraft(userId, projectId,
                new CreateBibleDraftRequest(reason, confirmed == null ? null : confirmed.getId()));
    }

    // ── Writing guides ──

    public List<WritingGuideView> listWritingGuides(Long userId, Long projectId, Long bibleVersionId) {
        accessService.require(projectId, userId, Action.VIEW);
        List<ProjectWritingGuide> guides = guideMapper.selectList(
                new LambdaQueryWrapper<ProjectWritingGuide>()
                        .eq(ProjectWritingGuide::getProjectId, projectId)
                        .eq(ProjectWritingGuide::getBibleVersionId, bibleVersionId)
                        .orderByAsc(ProjectWritingGuide::getScopeType, ProjectWritingGuide::getScopeId));
        return guides.stream().map(this::toWritingGuideView).toList();
    }

    @Transactional
    public WritingGuideView saveWritingGuide(Long userId, Long projectId, Long bibleVersionId,
                                             UpsertWritingGuideRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        CreativeBibleVersion bible = getBibleOrFail(bibleVersionId, projectId);
        if (!"draft".equals(bible.getStatus()) && !"reviewable".equals(bible.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已确认的创作圣经不可修改，请先创建新草稿");
        }

        // validate scope_type
        if (!Set.of("project", "character", "content_unit").contains(request.scopeType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无效的 scope_type: " + request.scopeType());
        }
        long scopeId = request.scopeId() != null ? request.scopeId() : 0L;
        if ("project".equals(request.scopeType()) && scopeId != 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "项目级口径 scope_id 必须为 0");
        }

        // find existing guide for same scope within this bible version
        ProjectWritingGuide existing = guideMapper.selectOne(
                new LambdaQueryWrapper<ProjectWritingGuide>()
                        .eq(ProjectWritingGuide::getProjectId, projectId)
                        .eq(ProjectWritingGuide::getBibleVersionId, bibleVersionId)
                        .eq(ProjectWritingGuide::getScopeType, request.scopeType())
                        .eq(ProjectWritingGuide::getScopeId, scopeId)
                        .orderByDesc(ProjectWritingGuide::getVersionNo)
                        .last("LIMIT 1"));

        ProjectWritingGuide guide = new ProjectWritingGuide();
        guide.setProjectId(projectId);
        guide.setBibleVersionId(bibleVersionId);
        guide.setScopeType(request.scopeType());
        guide.setScopeId(scopeId);
        guide.setGuideJson(toJson(request.guide()));
        guide.setParentGuideId(request.parentGuideId());
        guide.setSourceType("manual");
        guide.setStatus("draft");
        guide.setCreatedBy(userId);

        if (existing != null && "draft".equals(existing.getStatus())) {
            // update existing draft
            guide.setId(existing.getId());
            guide.setVersionNo(existing.getVersionNo());
            guideMapper.updateById(guide);
        } else {
            // create new version
            int nextVersion = existing == null ? 1 : existing.getVersionNo() + 1;
            guide.setVersionNo(nextVersion);
            guideMapper.insert(guide);
        }

        return toWritingGuideView(guide);
    }

    // ── Lifecycle ──

    @Transactional
    public BibleSummaryView submitReview(Long userId, Long projectId, Long bibleVersionId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        CreativeBibleVersion version = getBibleOrFail(bibleVersionId, projectId);
        if (!"draft".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有草稿可以提交审核");
        }
        version.setStatus("reviewable");
        bibleMapper.updateById(version);
        log.info("创作圣经提交审核: projectId={}, versionNo={}", projectId, version.getVersionNo());
        return toSummary(version);
    }

    @Transactional
    public void archive(Long userId, Long projectId, Long bibleVersionId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        CreativeBibleVersion version = getBibleOrFail(bibleVersionId, projectId);
        if (!"superseded".equals(version.getStatus()) && !"confirmed".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "只有已替代或已确认（无下游依赖）的版本可以归档");
        }
        // P0: stub dependency check; P1 adds full check against storyboard_masters/canvas_import_snapshots
        if ("confirmed".equals(version.getStatus())) {
            log.info("归档已确认版本 {}，下游依赖检查将在 P1 完善", bibleVersionId);
        }
        version.setStatus("archived");
        bibleMapper.updateById(version);
        log.info("创作圣经归档: projectId={}, versionNo={}", projectId, version.getVersionNo());
    }

    // ── helpers ──

    private CreativeBibleVersion findLatest(Long projectId, String status) {
        LambdaQueryWrapper<CreativeBibleVersion> query = new LambdaQueryWrapper<>();
        query.eq(CreativeBibleVersion::getProjectId, projectId);
        if (status != null) {
            query.eq(CreativeBibleVersion::getStatus, status);
        }
        query.orderByDesc(CreativeBibleVersion::getVersionNo);
        query.last("LIMIT 1");
        return bibleMapper.selectOne(query);
    }

    private CreativeBibleVersion findLatestExcept(Long projectId, String status, Long excludeId) {
        LambdaQueryWrapper<CreativeBibleVersion> query = new LambdaQueryWrapper<>();
        query.eq(CreativeBibleVersion::getProjectId, projectId);
        query.eq(CreativeBibleVersion::getStatus, status);
        query.ne(CreativeBibleVersion::getId, excludeId);
        query.orderByDesc(CreativeBibleVersion::getVersionNo);
        query.last("LIMIT 1");
        return bibleMapper.selectOne(query);
    }

    private CreativeBibleVersion getBibleOrFail(Long id, Long projectId) {
        CreativeBibleVersion bible = bibleMapper.selectById(id);
        if (bible == null || !bible.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "创作圣经版本不存在: " + id);
        }
        return bible;
    }

    private String buildSnapshot(Long projectId, Long bibleVersionId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        // 1. Ecosystem rules
        List<EcosystemRule> rules = ecosystemMapper.selectList(
                new LambdaQueryWrapper<EcosystemRule>()
                        .eq(EcosystemRule::getProjectId, projectId)
                        .eq(EcosystemRule::getBibleVersionId, bibleVersionId)
                        .orderByAsc(EcosystemRule::getRuleType, EcosystemRule::getId));
        snapshot.put("ecosystem_rules", rules.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("rule_type", r.getRuleType());
            m.put("name", r.getName());
            m.put("summary", r.getSummary());
            m.put("details", parseJson(r.getDetailsJson()));
            m.put("scope", parseJson(r.getScopeJson()));
            m.put("exceptions", parseJson(r.getExceptionsJson()));
            m.put("revision", r.getRevision());
            return m;
        }).toList());

        // 2. Confirmed settings with versions
        List<Map<String, Object>> settingSnapshots = new ArrayList<>();
        List<ProjectSettingEntity> confirmedSettings = settingMapper.selectList(
                new LambdaQueryWrapper<ProjectSettingEntity>()
                        .eq(ProjectSettingEntity::getProjectId, projectId)
                        .eq(ProjectSettingEntity::getStatus, "confirmed"));
        for (ProjectSettingEntity s : confirmedSettings) {
            ProjectSettingVersion ver = settingVersionMapper.selectOne(
                    new LambdaQueryWrapper<ProjectSettingVersion>()
                            .eq(ProjectSettingVersion::getEntityId, s.getId())
                            .eq(ProjectSettingVersion::getVersionNo, s.getCurrentVersionNo()));
            if (ver == null) {
                throw new BizException(ErrorCode.NOT_FOUND,
                        "实体设定版本缺失: entity=" + s.getId() + " v" + s.getCurrentVersionNo());
            }
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("entity_id", s.getId());
            sm.put("entity_type", s.getSettingType());
            sm.put("name", s.getCanonicalName());
            sm.put("version_no", ver.getVersionNo());
            sm.put("details", parseJson(ver.getSnapshotJson()));
            settingSnapshots.add(sm);
        }
        snapshot.put("confirmed_settings", settingSnapshots);

        // 3. Writing guides
        List<ProjectWritingGuide> guides = guideMapper.selectList(
                new LambdaQueryWrapper<ProjectWritingGuide>()
                        .eq(ProjectWritingGuide::getProjectId, projectId)
                        .eq(ProjectWritingGuide::getBibleVersionId, bibleVersionId));
        snapshot.put("writing_guides", guides.stream().map(g -> {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("id", g.getId());
            gm.put("scope_type", g.getScopeType());
            gm.put("scope_id", g.getScopeId());
            gm.put("version_no", g.getVersionNo());
            gm.put("guide", parseJson(g.getGuideJson()));
            return gm;
        }).toList());

        return toJson(snapshot);
    }

    private void copyFromSource(Long projectId, Long newBibleId, Long sourceVersionId) {
        CreativeBibleVersion source = bibleMapper.selectById(sourceVersionId);
        if (source == null || !source.getProjectId().equals(projectId)) {
            return;
        }
        // Copy ecosystem rules from source — create as draft in new bible
        List<EcosystemRule> sourceRules = ecosystemMapper.selectList(
                new LambdaQueryWrapper<EcosystemRule>()
                        .eq(EcosystemRule::getProjectId, projectId)
                        .eq(EcosystemRule::getBibleVersionId, sourceVersionId));
        for (EcosystemRule sr : sourceRules) {
            EcosystemRule copy = new EcosystemRule();
            copy.setProjectId(projectId);
            copy.setBibleVersionId(newBibleId);
            copy.setRuleType(sr.getRuleType());
            copy.setName(sr.getName());
            copy.setSummary(sr.getSummary());
            copy.setDetailsJson(sr.getDetailsJson());
            copy.setScopeJson(sr.getScopeJson());
            copy.setExceptionsJson(sr.getExceptionsJson());
            copy.setStatus("draft");
            copy.setSourceType("merged");
            copy.setEvidenceJson(toJson(Map.of("copied_from_rule_id", sr.getId())));
            copy.setRevision(0);
            copy.setCreatedBy(sr.getCreatedBy());
            ecosystemMapper.insert(copy);
        }
        // Copy confirmed writing guides
        List<ProjectWritingGuide> sourceGuides = guideMapper.selectList(
                new LambdaQueryWrapper<ProjectWritingGuide>()
                        .eq(ProjectWritingGuide::getProjectId, projectId)
                        .eq(ProjectWritingGuide::getBibleVersionId, sourceVersionId)
                        .eq(ProjectWritingGuide::getStatus, "confirmed"));
        for (ProjectWritingGuide sg : sourceGuides) {
            ProjectWritingGuide copy = new ProjectWritingGuide();
            copy.setProjectId(projectId);
            copy.setBibleVersionId(newBibleId);
            copy.setScopeType(sg.getScopeType());
            copy.setScopeId(sg.getScopeId());
            copy.setVersionNo(1);
            copy.setStatus("draft");
            copy.setGuideJson(sg.getGuideJson());
            copy.setParentGuideId(sg.getId());
            copy.setSourceType("merged");
            copy.setCreatedBy(sg.getCreatedBy());
            guideMapper.insert(copy);
        }
    }

    private BibleSummaryView toSummary(CreativeBibleVersion v) {
        return new BibleSummaryView(
                v.getId(), v.getProjectId(), v.getVersionNo(), v.getStatus(),
                v.getSourceVersionId(), v.getSummary(), v.getSnapshotHash(),
                v.getConfirmedBy(),
                v.getConfirmedAt() != null ? v.getConfirmedAt().toString() : null,
                v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
    }

    private EcosystemRuleView toEcosystemView(EcosystemRule r) {
        return new EcosystemRuleView(
                r.getId(), r.getProjectId(), r.getBibleVersionId(),
                r.getRuleType(), r.getName(), r.getSummary(),
                parseJson(r.getDetailsJson()), parseJson(r.getScopeJson()),
                parseJson(r.getExceptionsJson()),
                r.getStatus(), r.getSourceType(), parseJson(r.getEvidenceJson()),
                r.getRevision(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
                r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
    }

    private WritingGuideView toWritingGuideView(ProjectWritingGuide g) {
        return new WritingGuideView(
                g.getId(), g.getProjectId(), g.getBibleVersionId(),
                g.getScopeType(), g.getScopeId(), g.getVersionNo(),
                g.getStatus(), parseJson(g.getGuideJson()),
                g.getParentGuideId(), g.getSourceType(),
                g.getConfirmedBy(),
                g.getConfirmedAt() != null ? g.getConfirmedAt().toString() : null,
                g.getCreatedAt() != null ? g.getCreatedAt().toString() : null);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
