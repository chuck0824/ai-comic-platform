package com.aicp.module.contentproject;

import com.aicp.common.util.JwtUtil;
import com.aicp.common.util.RedisUtil;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentGenerationJob;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentGenerationJobMapper;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GenerationJobDecisionE2ETest {

    @Autowired MockMvc mvc;
    @Autowired ContentGenerationJobMapper jobMapper;
    @Autowired ContentUnitMapper unitMapper;
    @Autowired ContentVersionMapper versionMapper;
    @Autowired ContentProjectMapper projectMapper;
    @Autowired ProjectMemberMapper memberMapper;
    @MockBean JwtUtil jwtUtil;
    @MockBean RedisUtil redisUtil;

    @BeforeEach
    void authenticateTokens() {
        when(redisUtil.isTokenBlacklisted(anyString())).thenReturn(false);
        when(jwtUtil.validateToken(anyString())).thenReturn(true);
        when(jwtUtil.getUserId("owner-token")).thenReturn(501L);
        when(jwtUtil.getUserId("other-token")).thenReturn(502L);
        when(jwtUtil.getRole(anyString())).thenReturn("creator");
        when(jwtUtil.getPermissions(anyString())).thenReturn(List.of());
        when(jwtUtil.getUserUuid(anyString())).thenReturn("test-user");
        when(jwtUtil.getAccountType(anyString())).thenReturn("free_user");
    }

    @Test
    void acceptAndDiscardEndpointsPersistCandidateDecisions() throws Exception {
        Long acceptedProjectId = insertProject(501L, "采用候选项目");
        ContentUnit acceptedUnit = insertUnit(acceptedProjectId, 1);
        ContentGenerationJob acceptedJob = insertJob(acceptedProjectId, acceptedUnit.getId(), "completed", 7, null);
        ContentVersion acceptedCandidate = insertCandidate(acceptedProjectId, acceptedUnit.getId(), acceptedJob.getId(), 1, "candidate");

        mvc.perform(post("/api/v1/generation-jobs/{id}/accept", acceptedJob.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result_version_id").value(acceptedCandidate.getId()))
                .andExpect(jsonPath("$.data.artifact_ref").value("/content-units/" + acceptedUnit.getId() + "/versions/" + acceptedCandidate.getId()))
                .andExpect(jsonPath("$.data.result_disposition").value("accepted"));
        mvc.perform(post("/api/v1/generation-jobs/{id}/accept", acceptedJob.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk());
        assertThat(unitMapper.selectById(acceptedUnit.getId()).getCurrentVersionId()).isEqualTo(acceptedCandidate.getId());
        assertThat(versionMapper.selectById(acceptedCandidate.getId()).getStatus()).isEqualTo("accepted");

        Long discardedProjectId = insertProject(501L, "丢弃候选项目");
        ContentUnit discardedUnit = insertUnit(discardedProjectId, 1);
        ContentGenerationJob discardedJob = insertJob(discardedProjectId, discardedUnit.getId(), "completed", 4, null);
        ContentVersion discardedCandidate = insertCandidate(discardedProjectId, discardedUnit.getId(), discardedJob.getId(), 1, "candidate");
        mvc.perform(post("/api/v1/generation-jobs/{id}/discard", discardedJob.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result_disposition").value("discarded"));
        mvc.perform(post("/api/v1/generation-jobs/{id}/discard", discardedJob.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk());
        assertThat(unitMapper.selectById(discardedUnit.getId()).getCurrentVersionId()).isNull();
        assertThat(versionMapper.selectById(discardedCandidate.getId()).getStatus()).isEqualTo("discarded");
    }

    @Test
    void onlyOneCandidateCreatedFromTheSameUnitBaselineCanBeAccepted() throws Exception {
        Long projectId = insertProject(501L, "并发候选项目");
        ContentUnit unit = insertUnit(projectId, 1);
        ContentGenerationJob firstJob = insertJob(projectId, unit.getId(), "completed", 5, null);
        ContentGenerationJob secondJob = insertJob(projectId, unit.getId(), "completed", 5, null);
        ContentVersion firstCandidate = insertCandidate(projectId, unit.getId(), firstJob.getId(), 1, "candidate");
        ContentVersion secondCandidate = insertCandidate(projectId, unit.getId(), secondJob.getId(), 2, "candidate");

        mvc.perform(post("/api/v1/generation-jobs/{id}/accept", firstJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/generation-jobs/{id}/accept", secondJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isConflict());

        assertThat(unitMapper.selectById(unit.getId()).getCurrentVersionId()).isEqualTo(firstCandidate.getId());
        assertThat(versionMapper.selectById(firstCandidate.getId()).getStatus()).isEqualTo("accepted");
        assertThat(versionMapper.selectById(secondCandidate.getId()).getStatus()).isEqualTo("candidate");
    }

    @Test
    void jobViewAndProcessingCancellationExposeTruthfulState() throws Exception {
        Long projectId = insertProject(501L, "任务状态项目");
        ContentUnit unit = insertUnit(projectId, 1);
        ContentGenerationJob failed = insertJob(projectId, unit.getId(), "failed", 6, "AI_ERROR");
        insertCandidate(projectId, unit.getId(), failed.getId(), 1, "candidate");
        mvc.perform(get("/api/v1/generation-jobs/{id}", failed.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actual_credits").value(6))
                .andExpect(jsonPath("$.data.error_code").value("AI_ERROR"))
                .andExpect(jsonPath("$.data.error_message").isNotEmpty())
                .andExpect(jsonPath("$.data.result_version_id").doesNotExist())
                .andExpect(jsonPath("$.data.artifact_ref").doesNotExist());

        mvc.perform(get("/api/v1/generation-jobs/{id}", failed.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/generation-jobs/{id}", failed.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer other-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(43002));

        ContentGenerationJob processing = insertJob(projectId, unit.getId(), "processing", 0, null);
        mvc.perform(post("/api/v1/generation-jobs/{id}/cancel", processing.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/generation-jobs/{id}/cancel", processing.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk());
        assertThat(jobMapper.selectById(processing.getId()).getStatus()).isEqualTo("cancelled");
    }

    @Test
    void publicVersionEndpointsHideAndRejectUnadoptedCandidates() throws Exception {
        Long projectId = insertProject(501L, "版本隔离项目");
        ContentUnit unit = insertUnit(projectId, 1);
        ContentVersion accepted = insertCandidate(projectId, unit.getId(), null, 1, "accepted");
        ContentVersion candidate = insertCandidate(projectId, unit.getId(), null, 2, "candidate");
        insertCandidate(projectId, unit.getId(), null, 3, "discarded");

        mvc.perform(get("/api/v1/content-units/{id}/versions", unit.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(accepted.getId()));
        mvc.perform(post("/api/v1/content-units/{id}/versions/{versionId}/restore", unit.getId(), candidate.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner-token"))
                .andExpect(status().isBadRequest());
    }

    private Long insertProject(Long ownerId, String name) {
        ContentProject project = new ContentProject();
        project.setUuid(UUID.randomUUID().toString());
        project.setTenantType("personal");
        project.setTenantId(ownerId);
        project.setOwnerUserId(ownerId);
        project.setName(name);
        project.setCreationMode("short_drama");
        project.setSourceMode("ai_manual");
        project.setStoryboardIntentStatus("not_decided");
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLifecycleStatus("active");
        project.setRevision(0);
        project.setIsDeleted(0);
        projectMapper.insert(project);
        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(ownerId);
        member.setRole("owner");
        memberMapper.insert(member);
        return project.getId();
    }

    private ContentUnit insertUnit(Long projectId, int displayNo) {
        ContentUnit unit = new ContentUnit();
        unit.setStableKey(UUID.randomUUID().toString());
        unit.setProjectId(projectId);
        unit.setUnitType("script_body");
        unit.setDisplayNo(displayNo);
        unit.setTitle("生成候选测试");
        unit.setStatus("draft");
        unit.setRevision(0);
        unit.setIsDeleted(0);
        unitMapper.insert(unit);
        return unit;
    }

    private ContentGenerationJob insertJob(Long projectId, Long unitId, String status, int actualCredits, String errorCode) {
        ContentGenerationJob job = new ContentGenerationJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setProjectId(projectId);
        job.setJobType("script_body_generate");
        job.setTargetType("content_unit");
        job.setTargetId(unitId);
        job.setStatus(status);
        job.setInputSnapshotJson("{\"_generation_target\":{\"unit_id\":" + unitId
                + ",\"revision\":0,\"current_version_id\":null}}");
        job.setInputSnapshotHash(UUID.randomUUID().toString().replace("-", ""));
        job.setSchemaVersion("v1");
        job.setEstimatedCredits(10);
        job.setActualCredits(actualCredits);
        job.setErrorCode(errorCode);
        job.setIdempotencyKey(UUID.randomUUID().toString());
        job.setCreatedBy(501L);
        jobMapper.insert(job);
        return job;
    }

    private ContentVersion insertCandidate(Long projectId, Long unitId, Long jobId, int versionNo, String status) {
        ContentVersion version = new ContentVersion();
        version.setProjectId(projectId);
        version.setContentUnitId(unitId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setContentJson("{\"stageKey\":\"script_body\"}");
        version.setPlainText("候选剧本正文");
        version.setSource("ai_generated");
        version.setGenerationJobId(jobId);
        version.setContentHash(UUID.randomUUID().toString().replace("-", ""));
        version.setCreatedBy(501L);
        versionMapper.insert(version);
        return version;
    }
}
