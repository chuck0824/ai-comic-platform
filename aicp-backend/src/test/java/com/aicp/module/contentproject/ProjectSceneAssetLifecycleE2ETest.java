package com.aicp.module.contentproject;

import com.aicp.module.asset.entity.AssetVersion;
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
