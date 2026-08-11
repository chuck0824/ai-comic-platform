package com.aicp.module.contentproject;

import com.aicp.module.contentproject.entity.ContentGenerationJob;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentGenerationJobMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GenerationJobDecisionE2ETest {

    @Autowired MockMvc mvc;
    @Autowired ContentGenerationJobMapper jobMapper;
    @Autowired ContentUnitMapper unitMapper;
    @Autowired ContentVersionMapper versionMapper;

    @Test
    void acceptAndDiscardEndpointsPersistCandidateDecisions() throws Exception {
        ContentUnit acceptedUnit = insertUnit(901L, 1);
        ContentGenerationJob acceptedJob = insertJob(901L, acceptedUnit.getId(), "completed", 7, null);
        ContentVersion acceptedCandidate = insertCandidate(901L, acceptedUnit.getId(), acceptedJob.getId(), 1);

        mvc.perform(post("/api/v1/generation-jobs/{id}/accept", acceptedJob.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result_version_id").value(acceptedCandidate.getId()))
                .andExpect(jsonPath("$.data.artifact_ref").value("/content-units/" + acceptedUnit.getId() + "/versions/" + acceptedCandidate.getId()))
                .andExpect(jsonPath("$.data.result_disposition").value("accepted"));
        mvc.perform(post("/api/v1/generation-jobs/{id}/accept", acceptedJob.getId()))
                .andExpect(status().isOk());
        assertThat(unitMapper.selectById(acceptedUnit.getId()).getCurrentVersionId()).isEqualTo(acceptedCandidate.getId());
        assertThat(versionMapper.selectById(acceptedCandidate.getId()).getStatus()).isEqualTo("accepted");

        ContentUnit discardedUnit = insertUnit(902L, 1);
        ContentGenerationJob discardedJob = insertJob(902L, discardedUnit.getId(), "completed", 4, null);
        ContentVersion discardedCandidate = insertCandidate(902L, discardedUnit.getId(), discardedJob.getId(), 1);
        mvc.perform(post("/api/v1/generation-jobs/{id}/discard", discardedJob.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result_disposition").value("discarded"));
        mvc.perform(post("/api/v1/generation-jobs/{id}/discard", discardedJob.getId()))
                .andExpect(status().isOk());
        assertThat(unitMapper.selectById(discardedUnit.getId()).getCurrentVersionId()).isNull();
        assertThat(versionMapper.selectById(discardedCandidate.getId()).getStatus()).isEqualTo("discarded");
    }

    @Test
    void jobViewAndProcessingCancellationExposeTruthfulState() throws Exception {
        ContentUnit unit = insertUnit(903L, 1);
        ContentGenerationJob failed = insertJob(903L, unit.getId(), "failed", 6, "AI_ERROR");
        mvc.perform(get("/api/v1/generation-jobs/{id}", failed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actual_credits").value(6))
                .andExpect(jsonPath("$.data.error_code").value("AI_ERROR"))
                .andExpect(jsonPath("$.data.error_message").isNotEmpty());

        ContentGenerationJob processing = insertJob(903L, unit.getId(), "processing", 0, null);
        mvc.perform(post("/api/v1/generation-jobs/{id}/cancel", processing.getId()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/generation-jobs/{id}/cancel", processing.getId()))
                .andExpect(status().isOk());
        assertThat(jobMapper.selectById(processing.getId()).getStatus()).isEqualTo("cancelled");
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
        job.setInputSnapshotJson("{}");
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

    private ContentVersion insertCandidate(Long projectId, Long unitId, Long jobId, int versionNo) {
        ContentVersion version = new ContentVersion();
        version.setProjectId(projectId);
        version.setContentUnitId(unitId);
        version.setVersionNo(versionNo);
        version.setStatus("candidate");
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
