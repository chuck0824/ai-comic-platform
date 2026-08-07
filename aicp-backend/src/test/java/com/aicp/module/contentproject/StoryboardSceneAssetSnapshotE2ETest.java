package com.aicp.module.contentproject;

import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardScene;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardSceneMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionShotMapper;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
@Transactional
class StoryboardSceneAssetSnapshotE2ETest {

    @Autowired private MockMvc mvc;
    @Autowired private ContentProjectMapper projectMapper;
    @Autowired private ProjectMemberMapper memberMapper;
    @Autowired private StoryboardMapper storyboardMapper;
    @Autowired private StoryboardVersionMapper storyboardVersionMapper;
    @Autowired private StoryboardSceneMapper storyboardSceneMapper;
    @Autowired private StoryboardVersionShotMapper shotMapper;
    @Autowired private AssetVersionMapper assetVersionMapper;

    private final long ownerId = 731L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shotKeepsOldSnapshotWhenSceneAssetAdvances() throws Exception {
        Fixture fixture = fixture(true);
        AssetRef asset = createSceneAsset(fixture.projectId(), "青桥出租屋", "深夜停电");

        bindShot(fixture, asset, """
                {"lighting":"仅保留应急灯","fixed_props":{"clock":"east-wall"},
                 "prompt_fragment":"rain outside the east window"}
                """);
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}",
                        fixture.projectId(), asset.assetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lighting\":\"更新后的灯光\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/shots",
                        fixture.projectId(), fixture.storyboardId(), fixture.versionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sceneAssetVersionId").value(asset.versionId()))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.master.id").value("SCENE-ASSET-001"))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.master.name").value("青桥出租屋"))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.master.version").value(1))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.master.path", containsString("SCENE-ASSET-001")))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.variant.id").value("VAR-001"))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.variant.version").value(1))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.sceneOverride.lighting").value("仅保留应急灯"))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.continuityRules[0]").value("窗在东墙"))
                .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.finalPromptFragment", containsString("rain outside")));
    }

    @Test
    void storyboardLockExplainsMissingSceneAssetBindings() throws Exception {
        Fixture fixture = fixture(true);

        mvc.perform(post("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/lock",
                        fixture.projectId(), fixture.storyboardId(), fixture.versionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("场景资产")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(fixture.shotId()))))
                .andExpect(jsonPath("$.message", containsString("scene-asset")));
    }

    @Test
    void storyboardLockRejectsIncompleteSnapshotWithRepairAction() throws Exception {
        Fixture fixture = fixture(true);
        AssetRef asset = createSceneAsset(fixture.projectId(), "损坏快照场景", "晨光");
        bindShot(fixture, asset, "{}");
        StoryboardShot shot = shotMapper.selectById(fixture.shotId());
        shot.setSceneAssetSnapshot("{\"master\":{\"version\":1},\"variant\":{\"id\":\"VAR-001\",\"version\":1}}");
        shotMapper.updateById(shot);

        mvc.perform(post("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/lock",
                        fixture.projectId(), fixture.storyboardId(), fixture.versionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("快照")))
                .andExpect(jsonPath("$.message", containsString("scene-asset")));
    }

    @Test
    void bindingRejectsStoryboardVersionAndAssetOwnershipMismatches() throws Exception {
        Fixture fixture = fixture(true);
        Fixture otherStoryboard = fixtureInProject(fixture.projectId(), true);
        Fixture otherProject = fixture(true);
        AssetRef ownedAsset = createSceneAsset(fixture.projectId(), "本项目场景", "夜景");
        AssetRef foreignAsset = createSceneAsset(otherProject.projectId(), "外部项目场景", "日景");

        mvc.perform(put("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/shots/{shot}/scene-asset",
                        fixture.projectId(), otherStoryboard.storyboardId(), fixture.versionId(), fixture.shotId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindingJson(ownedAsset, "{}")))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/shots/{shot}/scene-asset",
                        fixture.projectId(), fixture.storyboardId(), fixture.versionId(), fixture.shotId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindingJson(foreignAsset, "{}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void continuityCheckReportsStaleVariantAndFixedPropConflictsWithRepairActions() throws Exception {
        Fixture fixture = fixture(true);
        AssetRef asset = createSceneAsset(fixture.projectId(), "连续性场景", "雨夜");
        bindShot(fixture, asset, """
                {"fixed_props":{"clock":"west-wall"},"prompt_fragment":"wet pavement"}
                """);

        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants/{variantId}",
                        fixture.projectId(), asset.assetId(), "VAR-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lighting_delta\":\"只留红色应急灯\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/continuity-check",
                        fixture.projectId(), fixture.storyboardId(), fixture.versionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issues[?(@.code == 'STALE_ASSET')].shotId").value(fixture.shotId().intValue()))
                .andExpect(jsonPath("$.data.issues[?(@.code == 'VARIANT_MISMATCH')].shotId").value(fixture.shotId().intValue()))
                .andExpect(jsonPath("$.data.issues[?(@.code == 'FIXED_PROP_CONFLICT')].shotId").value(fixture.shotId().intValue()))
                .andExpect(jsonPath("$.data.issues[0].repairAction").isNotEmpty());
    }

    private Fixture fixture(boolean withShot) {
        return fixtureInProject(createProject(), withShot);
    }

    private Fixture fixtureInProject(long projectId, boolean withShot) {
        authenticateAs(ownerId);
        Storyboard storyboard = new Storyboard();
        storyboard.setUuid(UUID.randomUUID().toString());
        storyboard.setProjectId(projectId);
        storyboard.setContentUnitId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        storyboard.setSourceContentVersionId(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        storyboard.setTitle("分镜");
        storyboard.setPurpose("default");
        storyboard.setProductionStatus("not_ready");
        storyboard.setCreatedBy(ownerId);
        storyboard.setIsDeleted(0);
        storyboardMapper.insert(storyboard);

        StoryboardVersion version = new StoryboardVersion();
        version.setUuid(UUID.randomUUID().toString());
        version.setStoryboardId(storyboard.getId());
        version.setSourceContentVersionId(storyboard.getSourceContentVersionId());
        version.setTier("A");
        version.setVersionNo(1);
        version.setStatus("draft");
        version.setRevision(0);
        version.setSchemaVersion(1);
        version.setTotalScenes(1);
        version.setTotalShots(withShot ? 1 : 0);
        version.setTotalDurationMs(withShot ? 3000L : 0L);
        version.setCreatedFrom("manual");
        version.setCreatedBy(ownerId);
        storyboardVersionMapper.insert(version);

        StoryboardScene scene = new StoryboardScene();
        scene.setVersionId(version.getId());
        scene.setSceneKey(UUID.randomUUID().toString());
        scene.setSceneNo(1);
        scene.setTitle("室内场");
        scene.setDurationMs(withShot ? 3000L : 0L);
        scene.setSortOrder(0);
        storyboardSceneMapper.insert(scene);

        Long shotId = null;
        if (withShot) {
            StoryboardShot shot = new StoryboardShot();
            shot.setUuid(UUID.randomUUID().toString());
            shot.setVersionId(version.getId());
            shot.setSceneId(scene.getId());
            shot.setShotKey(UUID.randomUUID().toString());
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);
            shot.setStatus("draft");
            shot.setSortOrder(0);
            shotMapper.insert(shot);
            shotId = shot.getId();
        }
        storyboard.setCurrentDraftVersionId(version.getId());
        storyboardMapper.updateById(storyboard);
        return new Fixture(projectId, storyboard.getId(), version.getId(), shotId);
    }

    private AssetRef createSceneAsset(long projectId, String name, String variantName) throws Exception {
        authenticateAs(ownerId);
        String body = mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","space_type":"INTERIOR","reusability":"PRIMARY",
                                 "reality_type":"REALISTIC","fixed_props":{"clock":"east-wall"},
                                 "continuity_rules":["窗在东墙"],"prompts":"cinematic room",
                                 "variants":[{"name":"%s","lighting_delta":"emergency light",
                                 "prompts":"night blackout"}]}
                                """.formatted(name, variantName)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long assetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        AssetVersion version = assetVersionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                        .eq(AssetVersion::getAssetId, assetId)
                        .eq(AssetVersion::getVersionNumber, 1));
        return new AssetRef(assetId, version.getId());
    }

    private void bindShot(Fixture fixture, AssetRef asset, String sceneOverride) throws Exception {
        mvc.perform(put("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/shots/{shot}/scene-asset",
                        fixture.projectId(), fixture.storyboardId(), fixture.versionId(), fixture.shotId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindingJson(asset, sceneOverride)))
                .andExpect(status().isOk());
    }

    private String bindingJson(AssetRef asset, String sceneOverride) {
        return """
                {"sceneAssetId":%d,"sceneAssetVersionId":%d,"sceneVariantId":"VAR-001",
                 "sceneVariantVersion":1,"sceneOverride":%s}
                """.formatted(asset.assetId(), asset.versionId(), sceneOverride);
    }

    private long createProject() {
        ContentProject project = new ContentProject();
        project.setUuid(UUID.randomUUID().toString());
        project.setTenantType("personal");
        project.setTenantId(ownerId);
        project.setOwnerUserId(ownerId);
        project.setName("分镜场景快照");
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
        owner.setUserId(ownerId);
        owner.setRole("owner");
        memberMapper.insert(owner);
        return project.getId();
    }

    private void authenticateAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, "test", java.util.List.of()));
    }

    private record Fixture(Long projectId, Long storyboardId, Long versionId, Long shotId) {}
    private record AssetRef(Long assetId, Long versionId) {}
}
