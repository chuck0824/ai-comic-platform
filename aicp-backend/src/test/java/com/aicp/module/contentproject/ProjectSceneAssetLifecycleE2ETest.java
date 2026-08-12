package com.aicp.module.contentproject;

import com.aicp.module.asset.entity.AssetApplication;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.mapper.AssetApplicationMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.contentproject.service.ProjectSceneAssetService;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

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
    @Autowired private ContentUnitMapper contentUnitMapper;
    @Autowired private StoryboardMapper storyboardMapper;
    @Autowired private StoryboardVersionMapper storyboardVersionMapper;
    @Autowired private StoryboardVersionShotMapper storyboardShotMapper;
    @Autowired private ProjectSceneAssetService sceneAssetService;

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
    void versionAdvancementKeepsApplicationsUndoableAndUsesPersistedSemanticStatusAcrossUpdateAndRestore() throws Exception {
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
                .andExpect(jsonPath("$.data.stale_references").value(0))
                .andExpect(jsonPath("$.data.references[0].sync_status").value("CURRENT"));
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

    @Test
    void locationConversionCreatesVariantAndObsidianProjection() throws Exception {
        long projectId = createProject(ownerId, "地点转换测试");
        authenticateAs(ownerId);
        long assetId = convertLocation(projectId, "WORLD-LOC-003", "青桥城中村出租屋");

        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"深夜停电\",\"time\":\"NIGHT\",\"lighting_delta\":\"仅应急灯\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].id").value("VAR-001"))
                .andExpect(jsonPath("$.data.current_version_no").value(2));

        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/markdown", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("04-场景资产/SCENE-ASSET-001-青桥城中村出租屋.md"))
                .andExpect(jsonPath("$.data.content", containsString("[[03-小说分析/世界观/主要地点]]")))
                .andExpect(jsonPath("$.data.content", containsString("VAR-001")));
    }

    @Test
    void genericScenePatchRejectsVariantReplacementWithoutAppendingVersion() throws Exception {
        long projectId = createProject(ownerId, "变体专用接口测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "变体专用场景", "基础灯光");

        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"name\":\"绕过专用接口\"}]}"))
                .andExpect(status().isBadRequest());

        assertThat(versionMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId))).isEqualTo(1L);
    }

    @Test
    void variantIdsRemainMonotonicAfterRestoreToPreVariantVersion() throws Exception {
        long projectId = createProject(ownerId, "变体 ID 恢复测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "恢复场景", "基础灯光");
        AssetVersion preVariant = versionFor(assetId, 1);

        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"首个变体\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].id").value("VAR-001"));

        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/versions/{versionId}/restore",
                        projectId, assetId, preVariant.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"恢复后的变体\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].id").value("VAR-002"));

        assertThat(versionFor(assetId, 2).getMetadata()).contains("\"id\":\"VAR-001\"");
        assertThat(versionFor(assetId, 4).getMetadata()).contains("\"id\":\"VAR-002\"");
    }

    @Test
    void markdownUsesTrustedPersistedConsumerLinksAndSafeUntrustedReferencesAndBasenames() throws Exception {
        long projectId = createProject(ownerId, "Markdown 链接测试");
        authenticateAs(ownerId);
        String created = mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":".","space_type":"INTERIOR","reusability":"PRIMARY",
                                "reality_type":"REALISTIC","references":["[[恶意目标]]"]}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long assetId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        ContentUnit unit = persistedContentUnit(projectId);
        AssetApplication application = new AssetApplication();
        application.setWorkspaceId("project_" + projectId);
        application.setAssetId(assetId);
        application.setAssetVersionId(versionFor(assetId, 1).getId());
        application.setProjectId(projectId);
        application.setTargetType("CONTENT_UNIT");
        application.setTargetId(unit.getId());
        application.setIdempotencyKey(UUID.randomUUID().toString());
        application.setStatus("APPLIED");
        applicationMapper.insert(application);

        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/markdown", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("04-场景资产/SCENE-ASSET-001-unnamed-scene.md"))
                .andExpect(jsonPath("$.data.content", containsString(
                        "[[06-剧本正文/UNIT-001.md|第一集]]")))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.not(containsString("[[恶意目标]]"))));
    }

    @Test
    void markdownOrdersTrustedConsumerLinksByPathThenAliasRegardlessOfInsertOrder() throws Exception {
        long projectId = createProject(ownerId, "链接排序测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "排序场景", "基础灯光");
        ContentUnit zulu = persistedContentUnit(projectId, "UNIT-Z", "Zulu");
        ContentUnit alpha = persistedContentUnit(projectId, "UNIT-A", "Alpha");
        persistedContentUnitApplication(projectId, assetId, zulu);
        persistedContentUnitApplication(projectId, assetId, alpha);

        String markdown = markdownContent(projectId, assetId);
        String alphaLink = "[[06-剧本正文/UNIT-A.md|Alpha]]";
        String zuluLink = "[[06-剧本正文/UNIT-Z.md|Zulu]]";
        assertThat(markdown).contains("- 消费者：" + alphaLink + "\n- 消费者：" + zuluLink);
        assertThat(markdown.indexOf(alphaLink)).isLessThan(markdown.indexOf(zuluLink));
    }

    @Test
    void markdownNeutralizesControlAndWikiSyntaxInTrustedConsumerAliases() throws Exception {
        long projectId = createProject(ownerId, "链接别名安全测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "别名场景", "基础灯光");
        ContentUnit malicious = persistedContentUnit(projectId, "UNIT-M", "恶意|别名]]\r\n伪造\u0001[../\\#");
        persistedContentUnitApplication(projectId, assetId, malicious);

        String markdown = markdownContent(projectId, assetId);
        String prefix = "[[06-剧本正文/UNIT-M.md|";
        int aliasStart = markdown.indexOf(prefix) + prefix.length();
        String alias = markdown.substring(aliasStart, markdown.indexOf("]]", aliasStart));
        assertThat(alias).isEqualTo("恶意｜别名］］伪造［·／＼＃");
        assertThat(alias).doesNotContain("..", "/", "\\", "#", "|", "[", "]", "\r", "\n", "\u0001");
    }

    @Test
    void listSearchesSceneMetadataAndReturnsAuthoritativeReferenceSummary() throws Exception {
        long projectId = createProject(ownerId, "场景搜索契约测试");
        authenticateAs(ownerId);
        String created = mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"普通房间","space_type":"INTERIOR","reusability":"HIGH",
                                 "reality_type":"REALISTIC","world_location_ref":"下城区地铁站",
                                 "landmarks":["红色楼梯"],"tags":["主场景","追逐"]}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long assetId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        ContentUnit unit = persistedContentUnit(projectId, "UNIT-SEARCH", "地铁追逐");
        persistedContentUnitApplication(projectId, assetId, unit);
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lighting\":\"夜间警示灯\"}"))
                .andExpect(status().isOk());

        for (String keyword : java.util.List.of("下城区", "红色楼梯", "主场景")) {
            mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets", projectId)
                            .param("keyword", keyword).param("referenced", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(assetId))
                    .andExpect(jsonPath("$.data[0].reference_count").value(1))
                    .andExpect(jsonPath("$.data[0].episode_references[0]").value("第1集 · 地铁追逐"))
                    .andExpect(jsonPath("$.data[0].sync_status").value("STALE"));
        }
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets", projectId)
                        .param("referenced", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void managementOnlyUpdateRemainsCurrentAfterApiReload() throws Exception {
        long projectId = createProject(ownerId, "管理字段同步契约测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "管理字段场景", "白天");
        ContentUnit unit = persistedContentUnit(projectId, "UNIT-MANAGEMENT", "管理字段集");
        persistedContentUnitApplication(projectId, assetId, unit);

        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[\"重要场景\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sync_status").value("CURRENT"));
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sync_status").value("CURRENT"));
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sync_status").value("CURRENT"));
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stale_references").value(0))
                .andExpect(jsonPath("$.data.references[0].sync_status").value("CURRENT"));
    }

    @Test
    void scriptSceneImpactUsesExactStableConsumerIdentity() throws Exception {
        long projectId = createProject(ownerId, "正文场景消费者契约测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "正文场景", "白天");
        AssetApplication application = new AssetApplication();
        application.setWorkspaceId("project_" + projectId);
        application.setAssetId(assetId);
        application.setAssetVersionId(versionFor(assetId, 1).getId());
        application.setProjectId(projectId);
        application.setTargetType("SCRIPT_SCENE");
        application.setTargetId(9001L);
        application.setTargetKey("EP-007-SCENE-001");
        application.setIdempotencyKey(UUID.randomUUID().toString());
        application.setStatus("APPLIED");
        applicationMapper.insert(application);

        AssetApplication legacyApplication = new AssetApplication();
        legacyApplication.setWorkspaceId("project_" + projectId);
        legacyApplication.setAssetId(assetId);
        legacyApplication.setAssetVersionId(versionFor(assetId, 1).getId());
        legacyApplication.setProjectId(projectId);
        legacyApplication.setTargetType("SCRIPT_SCENE");
        legacyApplication.setTargetId(9002L);
        legacyApplication.setIdempotencyKey(UUID.randomUUID().toString());
        legacyApplication.setStatus("APPLIED");
        applicationMapper.insert(legacyApplication);

        String body = mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> refs = JsonPath.read(body, "$.data.references");
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.get("type")).isEqualTo("SCRIPT_SCENE");
            assertThat(ref.get("consumer_id")).isEqualTo(9001);
            assertThat(ref.get("consumer_key")).isEqualTo("EP-007-SCENE-001");
        });
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.get("type")).isEqualTo("SCRIPT_SCENE");
            assertThat(ref.get("consumer_id")).isEqualTo(9002);
            assertThat(ref.get("consumer_key")).isEqualTo("9002");
        });
    }

    @Test
    void reviewingCurrentDraftRemainsAnActiveConsumerAndBlocksArchive() throws Exception {
        long projectId = createProject(ownerId, "待审分镜活跃引用测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "待审场景", "白天");
        long assetVersionId = versionFor(assetId, 1).getId();
        ContentUnit unit = persistedContentUnit(projectId, "UNIT-REVIEWING", "待审分镜");
        Storyboard storyboard = persistedStoryboard(projectId, unit, "reviewing-current-draft");
        StoryboardVersion draft = persistedStoryboardVersion(storyboard, "draft", 1);
        persistedStoryboardShot(draft, assetId, assetVersionId, "SHOT-REVIEWING");
        storyboard.setCurrentDraftVersionId(draft.getId());
        storyboardMapper.updateById(storyboard);

        mvc.perform(post("/api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/submit-review",
                        projectId, storyboard.getId(), draft.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("reviewing"));

        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.references.length()").value(1))
                .andExpect(jsonPath("$.data.references[0].consumer_key").value("SHOT-REVIEWING"));
        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/archive", projectId, assetId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("仍被引用")));
    }

    @Test
    void impactEnumeratesUnlockedAndLockedModernStoryboardConsumers() throws Exception {
        long projectId = createProject(ownerId, "分镜影响契约测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "站台", "白天");
        long oldVersionId = versionFor(assetId, 1).getId();
        ContentUnit unit = persistedContentUnit(projectId, "UNIT-SB", "站台危机");
        persistedStoryboardShot(projectId, unit, assetId, oldVersionId, "draft", "SHOT-DRAFT");
        persistedStoryboardShot(projectId, unit, assetId, oldVersionId, "locked", "SHOT-LOCKED");
        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lighting\":\"深夜\"}"))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> refs = JsonPath.read(body, "$.data.references");
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.get("type")).isEqualTo("STORYBOARD_SHOT");
            assertThat(ref.get("consumer_key")).isEqualTo("SHOT-DRAFT");
            assertThat(ref.get("locked")).isEqualTo(false);
            assertThat(ref.get("sync_status")).isEqualTo("NEEDS_SYNC");
        }).anySatisfy(ref -> {
            assertThat(ref.get("type")).isEqualTo("STORYBOARD_SHOT");
            assertThat(ref.get("consumer_key")).isEqualTo("SHOT-LOCKED");
            assertThat(ref.get("locked")).isEqualTo(true);
            assertThat(ref.get("snapshot_locked")).isEqualTo(true);
            assertThat(ref.get("sync_status")).isEqualTo("PINNED");
            assertThat(ref.get("snapshot_fingerprint")).isEqualTo("fp-SHOT-LOCKED");
        });
    }

    @Test
    void forkAndSupersedeImpactIncludesOnlyCurrentDraftAndCurrentImmutableLockedConsumer() throws Exception {
        long projectId = createProject(ownerId, "分镜活跃指针契约测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "分镜版本场景", "白天");
        long oldVersionId = versionFor(assetId, 1).getId();
        ContentUnit unit = persistedContentUnit(projectId, "UNIT-FORK", "派生分镜");

        Storyboard storyboard = persistedStoryboard(projectId, unit, "fork-supersede");
        StoryboardVersion historical = persistedStoryboardVersion(storyboard, "superseded", 1);
        persistedStoryboardShot(historical, assetId, oldVersionId, "SHOT-HISTORICAL");
        StoryboardVersion immutableCurrent = persistedStoryboardVersion(storyboard, "superseded", 2);
        persistedStoryboardShot(immutableCurrent, assetId, oldVersionId, "SHOT-CURRENT-LOCKED");
        StoryboardVersion forkDraft = persistedStoryboardVersion(storyboard, "draft", 3);
        persistedStoryboardShot(forkDraft, assetId, oldVersionId, "SHOT-CURRENT-DRAFT");
        storyboard.setCurrentLockedVersionId(immutableCurrent.getId());
        storyboard.setCurrentDraftVersionId(forkDraft.getId());
        storyboardMapper.updateById(storyboard);

        mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lighting\":\"深夜\"}"))
                .andExpect(status().isOk());
        String body = mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact", projectId, assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.references.length()").value(2))
                .andExpect(jsonPath("$.data.locked_references").value(1))
                .andExpect(jsonPath("$.data.stale_references").value(1))
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> keys = JsonPath.read(body, "$.data.references[*].consumer_key");
        assertThat(keys).containsExactlyInAnyOrder("SHOT-CURRENT-LOCKED", "SHOT-CURRENT-DRAFT")
                .doesNotContain("SHOT-HISTORICAL");
        java.util.List<java.util.Map<String, Object>> refs = JsonPath.read(body, "$.data.references");
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.get("consumer_key")).isEqualTo("SHOT-CURRENT-LOCKED");
            assertThat(ref.get("sync_status")).isEqualTo("PINNED");
            assertThat(ref.get("locked")).isEqualTo(true);
        }).anySatisfy(ref -> {
            assertThat(ref.get("consumer_key")).isEqualTo("SHOT-CURRENT-DRAFT");
            assertThat(ref.get("sync_status")).isEqualTo("NEEDS_SYNC");
            assertThat(ref.get("locked")).isEqualTo(false);
        });
    }

    @Test
    void referencedSceneCanBeReversiblyDisabledButArchiveRequiresMigration() throws Exception {
        long projectId = createProject(ownerId, "场景停用契约测试");
        authenticateAs(ownerId);
        long assetId = createSceneAsset(projectId, "仍被引用的场景", "白天");
        ContentUnit unit = persistedContentUnit(projectId, "UNIT-DISABLE", "停用测试");
        persistedContentUnitApplication(projectId, assetId, unit);

        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/archive", projectId, assetId))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/disable", projectId, assetId))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        com.aicp.common.exception.BizException disabledBinding = org.junit.jupiter.api.Assertions.assertThrows(com.aicp.common.exception.BizException.class,
                () -> sceneAssetService.resolveStoryboardSnapshot(ownerId, projectId, assetId,
                        versionFor(assetId, 1).getId(), null, null, java.util.Map.of()));
        assertThat(disabledBinding.getCode()).isEqualTo(48009);
        assertThat(disabledBinding.getMessage()).contains("已停用");
        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/activate", projectId, assetId))
                .andExpect(status().isOk());

        AssetApplication application = applicationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetApplication>()
                        .eq(AssetApplication::getAssetId, assetId));
        application.setStatus("UNDONE");
        applicationMapper.updateById(application);
        mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/archive", projectId, assetId))
                .andExpect(status().isOk());
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

    private long convertLocation(long projectId, String worldLocationRef, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/from-location", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"world_location_ref":"%s","name":"%s"}
                                """.formatted(worldLocationRef, name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private AssetVersion versionFor(long assetId, int versionNo) {
        return versionMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId).eq(AssetVersion::getVersionNumber, versionNo));
    }

    private ContentUnit persistedContentUnit(long projectId) {
        return persistedContentUnit(projectId, "UNIT-001", "第一集");
    }

    private ContentUnit persistedContentUnit(long projectId, String stableKey, String title) {
        ContentUnit unit = new ContentUnit();
        unit.setStableKey(stableKey);
        unit.setProjectId(projectId);
        unit.setUnitType("EPISODE");
        unit.setDisplayNo(contentUnitMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, "EPISODE")).intValue() + 1);
        unit.setTitle(title);
        unit.setStatus("draft");
        unit.setRevision(0);
        unit.setIsDeleted(0);
        contentUnitMapper.insert(unit);
        return unit;
    }

    private void persistedContentUnitApplication(long projectId, long assetId, ContentUnit unit) {
        AssetApplication application = new AssetApplication();
        application.setWorkspaceId("project_" + projectId);
        application.setAssetId(assetId);
        application.setAssetVersionId(versionFor(assetId, 1).getId());
        application.setProjectId(projectId);
        application.setTargetType("CONTENT_UNIT");
        application.setTargetId(unit.getId());
        application.setIdempotencyKey(UUID.randomUUID().toString());
        application.setStatus("APPLIED");
        applicationMapper.insert(application);
    }

    private void persistedStoryboardShot(long projectId, ContentUnit unit, long assetId, long assetVersionId,
                                          String versionStatus, String shotCode) {
        Storyboard storyboard = persistedStoryboard(projectId, unit, shotCode);
        StoryboardVersion version = persistedStoryboardVersion(storyboard, versionStatus, 1);
        persistedStoryboardShot(version, assetId, assetVersionId, shotCode);
        if ("draft".equalsIgnoreCase(versionStatus)) storyboard.setCurrentDraftVersionId(version.getId());
        else if (com.aicp.module.storyboard.domain.StoryboardStateMachine.isLocked(
                com.aicp.module.storyboard.domain.StoryboardEnums.VersionStatus.valueOf(versionStatus.toUpperCase()))) {
            storyboard.setCurrentLockedVersionId(version.getId());
        }
        storyboardMapper.updateById(storyboard);
    }

    private Storyboard persistedStoryboard(long projectId, ContentUnit unit, String key) {
        Storyboard storyboard = new Storyboard();
        storyboard.setUuid(UUID.randomUUID().toString());
        storyboard.setProjectId(projectId);
        storyboard.setContentUnitId(unit.getId());
        storyboard.setSourceContentVersionId((long) Math.abs(key.hashCode()));
        storyboard.setTitle(key);
        storyboard.setPurpose(key);
        storyboard.setProductionStatus("not_ready");
        storyboard.setCreatedBy(ownerId);
        storyboard.setIsDeleted(0);
        storyboardMapper.insert(storyboard);
        return storyboard;
    }

    private StoryboardVersion persistedStoryboardVersion(Storyboard storyboard, String versionStatus, int versionNo) {
        StoryboardVersion version = new StoryboardVersion();
        version.setUuid(UUID.randomUUID().toString());
        version.setStoryboardId(storyboard.getId());
        version.setSourceContentVersionId(storyboard.getSourceContentVersionId());
        version.setTier("A");
        version.setVersionNo(versionNo);
        version.setStatus(versionStatus);
        version.setRevision(0);
        version.setSchemaVersion(1);
        version.setTotalScenes(1);
        version.setTotalShots(1);
        version.setTotalDurationMs(1000L);
        version.setCreatedFrom("manual");
        version.setCreatedBy(ownerId);
        if ("locked".equals(versionStatus)) {
            version.setLockedBy(ownerId);
            version.setLockedAt(java.time.LocalDateTime.now());
        }
        storyboardVersionMapper.insert(version);
        return version;
    }

    private StoryboardShot persistedStoryboardShot(StoryboardVersion version, long assetId, long assetVersionId,
                                                    String shotCode) {
        StoryboardShot shot = new StoryboardShot();
        shot.setUuid(UUID.randomUUID().toString());
        shot.setVersionId(version.getId());
        shot.setSceneId(1L);
        shot.setShotKey(UUID.randomUUID().toString());
        shot.setShotCode(shotCode);
        shot.setDurationMs(1000L);
        shot.setSceneAssetId(assetId);
        shot.setSceneAssetVersionId(assetVersionId);
        shot.setSceneAssetSnapshot("{\"fingerprint\":\"fp-" + shotCode + "\"}");
        shot.setStatus("draft");
        shot.setSortOrder(1);
        storyboardShotMapper.insert(shot);
        return shot;
    }

    private String markdownContent(long projectId, long assetId) throws Exception {
        String response = mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/markdown", projectId, assetId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$.data.content");
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
