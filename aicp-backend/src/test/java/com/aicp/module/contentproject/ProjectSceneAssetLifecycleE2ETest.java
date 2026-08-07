package com.aicp.module.contentproject;

import com.aicp.module.asset.entity.AssetApplication;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.mapper.AssetApplicationMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
@Transactional
class ProjectSceneAssetLifecycleE2ETest {

    @Autowired private MockMvc mvc;
    @Autowired private ContentProjectMapper projectMapper;
    @Autowired private ProjectMemberMapper memberMapper;
    @Autowired private AssetVersionMapper versionMapper;
    @Autowired private AssetApplicationMapper applicationMapper;

    private final long ownerId = 501L;
    private final long otherUserId = 502L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sceneAssetIsProjectScopedVersionedAndRestorable() throws Exception {
        long projectId = createProject(ownerId, "场景资产测试");
        authenticateAs(ownerId);
        String created = mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"青桥城中村出租屋","space_type":"INTERIOR",
                                 "reusability":"PRIMARY","reality_type":"REALISTIC",
                                 "layout":"一室一厅，门口正对客厅","continuity_rules":["窗在东墙"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content_project_id").value(projectId))
                .andExpect(jsonPath("$.data.asset_type").value("SCENE"))
                .andExpect(jsonPath("$.data.current_version_no").value(1))
                .andReturn().getResponse().getContentAsString();

        long assetId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lighting\":\"深夜冷色顶灯\",\"change_note\":\"补充夜景基础灯光\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_version_no").value(2));

        AssetVersion currentVersion = versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, assetId)
                        .eq(AssetVersion::getVersionNumber, 2));
        org.assertj.core.api.Assertions.assertThat(currentVersion.getMetadata())
                .contains("\"schema_version\":1", "\"lighting\":\"深夜冷色顶灯\"");

        authenticateAs(otherUserId);
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets", projectId))
                .andExpect(status().isForbidden());
    }

    @Test
    void versionAdvancementKeepsApplicationsUndoableAndReportsStaleAcrossUpdateAndRestore() throws Exception {
        long projectId = createProject(ownerId, "场景引用过期测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "旧版本场景", "原始灯光");
        AssetVersion oldVersion = versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, assetId)
                        .eq(AssetVersion::getVersionNumber, 1));
        AssetApplication application = new AssetApplication();
        application.setWorkspaceId("project_" + projectId);
        application.setAssetId(assetId);
        application.setAssetVersionId(oldVersion.getId());
        application.setProjectId(projectId);
        application.setIdempotencyKey(UUID.randomUUID().toString());
        application.setStatus("APPLIED");
        applicationMapper.insert(application);

        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lighting\":\"更新后的夜景灯光\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_version_no").value(2));

        assertThat(applicationMapper.selectById(application.getId()).getStatus()).isEqualTo("APPLIED");
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stale_references").value(1))
                .andExpect(jsonPath("$.data.references[0].sync_status").value("NEEDS_SYNC"));

        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/versions/{versionId}/restore",
                        projectId, assetId, oldVersion.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(applicationMapper.selectById(application.getId()).getStatus()).isEqualTo("APPLIED");
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stale_references").value(1))
                .andExpect(jsonPath("$.data.references[0].sync_status").value("NEEDS_SYNC"));
    }

    @Test
    void rejectsInvalidOrNoopUpdatesAndInvalidNestedVariantsWithoutAppendingVersion() throws Exception {
        long projectId = createProject(ownerId, "场景更新校验测试");
        authenticateAs(ownerId);
        mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"无效变体","space_type":"INTERIOR","reusability":"PRIMARY",
                                "reality_type":"REALISTIC","variants":[{"name":"   "}]}
                                """))
                .andExpect(status().isBadRequest());

        long assetId = createSceneAsset(projectId, "校验场景", "原始灯光");
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"space_type\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"variants\":[{\"name\":\" \"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lighting\":\"原始灯光\"}"))
                .andExpect(status().isBadRequest());

        Long versionCount = versionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, assetId));
        assertThat(versionCount).isEqualTo(1L);
    }

    @Test
    void rejectsCorruptCurrentOrHistoricalMetadataWithoutAppendingVersion() throws Exception {
        long projectId = createProject(ownerId, "场景元数据校验测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "损坏元数据场景", "原始灯光");
        AssetVersion version = versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, assetId)
                        .eq(AssetVersion::getVersionNumber, 1));
        version.setMetadata("[]");
        versionMapper.updateById(version);

        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lighting\":\"新灯光\"}"))
                .andExpect(status().isBadRequest());
        assertThat(versionMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId))).isEqualTo(1L);

        long historicalAssetId = createSceneAsset(projectId, "历史元数据场景", "初始灯光");
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, historicalAssetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lighting\":\"当前灯光\"}"))
                .andExpect(status().isOk());
        AssetVersion historicalVersion = versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, historicalAssetId)
                        .eq(AssetVersion::getVersionNumber, 1));
        historicalVersion.setMetadata("[]");
        versionMapper.updateById(historicalVersion);
        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/versions/{versionId}/restore",
                        projectId, historicalAssetId, historicalVersion.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        Long versionCount = versionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, historicalAssetId));
        assertThat(versionCount).isEqualTo(2L);
    }

    private long createSceneAsset(long projectId, String name, String lighting) throws Exception {
        String body = mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","space_type":"INTERIOR","reusability":"PRIMARY",
                                "reality_type":"REALISTIC","lighting":"%s"}
                                """.formatted(name, lighting)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createProject(long ownerUserId, String name) {
        ContentProject project = new ContentProject();
        project.setUuid(UUID.randomUUID().toString());
        project.setTenantType("personal");
        project.setTenantId(ownerUserId);
        project.setOwnerUserId(ownerUserId);
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

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(ownerUserId);
        owner.setRole("owner");
        memberMapper.insert(owner);
        return project.getId();
    }

    private void authenticateAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, "test", java.util.List.of()));
    }
}
