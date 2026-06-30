package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorldbuildingService 单元测试")
class WorldbuildingServiceTest {

    @Mock CharacterProfileMapper charMapper;
    @Mock PlotTaskMapper taskMapper;
    @Mock VolumeOutlineMapper volumeMapper;
    @Mock WorldLocationMapper locationMapper;
    @Mock StoryTimelineMapper timelineMapper;
    @Mock ForeshadowingItemMapper foreshadowMapper;
    @Mock ContentUnitMapper unitMapper;
    @Mock AiRouter aiRouter;
    @Mock AiResponseParser parser;

    @InjectMocks
    WorldbuildingService service;

    @BeforeEach
    void setUp() {
        // Default parser stubs — use lenient to avoid UnnecessaryStubbing in strict mode
        lenient().when(parser.str(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            return arg != null ? String.valueOf(arg) : "";
        });
        lenient().when(parser.toInt(any(), anyInt())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            int def = inv.getArgument(1);
            if (arg instanceof Number n) return n.intValue();
            return def;
        });
        lenient().when(parser.toJson(any())).thenReturn("[]");
        lenient().when(parser.ellipsis(any(), anyInt())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            int max = inv.getArgument(1);
            return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
        });
    }

    // ===== Phase 1.1: aiGeneratePlotTasks =====

    @Test
    @DisplayName("aiGeneratePlotTasks — 填充所有字段：cost、stageGoals(goal单数)、taskType")
    void aiGeneratePlotTasks_populatesAllFields() {
        String aiJson = "{\"main_quest\":{\"cost\":\"生命代价\"},"
                + "\"stage_tasks\":[{\"title\":\"潜入敌营\",\"goal\":\"获取情报\","
                + "\"obstacle\":\"守卫森严\",\"character\":\"主角\",\"type\":\"main\",\"cost\":\"受伤\"}]}";
        Map<String, Object> parsed = Map.of(
                "main_quest", Map.of("cost", "生命代价"),
                "stage_tasks", List.of(Map.of(
                        "title", "潜入敌营", "goal", "获取情报",
                        "obstacle", "守卫森严", "character", "主角",
                        "type", "main", "cost", "受伤")));

        when(aiRouter.chatCompletion(any())).thenReturn(Map.of("choices", List.of()));
        when(parser.extractText(any())).thenReturn(aiJson);
        when(parser.parseJson(aiJson)).thenReturn(parsed);
        when(parser.toJson(any())).thenReturn("\"获取情报\"");
        doAnswer(inv -> { PlotTask p = inv.getArgument(0); p.setId(1L); return 1; })
                .when(taskMapper).insert(any(PlotTask.class));

        int count = service.aiGeneratePlotTasks(1L, "故事上下文");

        assertThat(count).isEqualTo(1);
        verify(taskMapper).insert(ArgumentMatchers.argThat(pt ->
                "潜入敌营".equals(pt.getTitle())
                && "main".equals(pt.getTaskType())
                && "受伤".equals(pt.getCost())
                && pt.getStageGoals() != null
                && pt.getDescription().equals("获取情报")
        ));
    }

    // ===== Phase 1.2: aiGenerateVolumes =====

    @Test
    @DisplayName("aiGenerateVolumes — 重新生成前清理旧数据 + 填充 chapterCount")
    void aiGenerateVolumes_clearsOldAndPopulatesChapterCount() {
        Map<String, Object> parsed = Map.of("volumes", List.of(
                Map.of("volume_no", 1, "title", "卷一", "goal", "建立世界观",
                        "turns", "三转", "volume_end_hook", "悬念", "character_changes", "成长",
                        "chapter_count", 12)));

        when(aiRouter.chatCompletion(any())).thenReturn(Map.of());
        when(parser.extractText(any())).thenReturn("{}");
        when(parser.parseJson(any())).thenReturn(parsed);
        when(parser.str(any())).thenCallRealMethod();
        when(parser.toInt(any(), anyInt())).thenCallRealMethod();
        doAnswer(inv -> { VolumeOutline v = inv.getArgument(0); v.setId(1L); return 1; })
                .when(volumeMapper).insert(any(VolumeOutline.class));

        int count = service.aiGenerateVolumes(1L, 3, "上下文");

        assertThat(count).isEqualTo(1);
        // Verify old volumes were cleared
        verify(volumeMapper).delete(any(LambdaQueryWrapper.class));
        verify(volumeMapper).insert(ArgumentMatchers.argThat(v ->
                v.getChapterCount() == 12 && "卷一".equals(v.getTitle())));
    }

    // ===== Phase 1.3: aiExtractLocations =====

    @Test
    @DisplayName("aiExtractLocations — 填充 L0/L1 全部 6 个新增字段")
    void aiExtractLocations_populatesAllFields() {
        Map<String, Object> parsed = Map.of("locations", List.of(
                Map.of("name", "青云城", "type", "主城", "description", "繁华都市",
                        "parent", "", "tier", "L1",
                        "distance_from_origin", "300里", "transportation", "传送阵",
                        "faction_territory", "正道联盟", "visual_reference", "云雾缭绕")));

        when(aiRouter.chatCompletion(any())).thenReturn(Map.of());
        when(parser.extractText(any())).thenReturn("{}");
        when(parser.parseJson(any())).thenReturn(parsed);
        when(parser.str(any())).thenCallRealMethod();
        when(parser.ellipsis(any(), anyInt())).thenCallRealMethod();
        doAnswer(inv -> { WorldLocation w = inv.getArgument(0); w.setId(1L); return 1; })
                .when(locationMapper).insert(any(WorldLocation.class));

        int count = service.aiExtractLocations(1L, "故事文本");

        assertThat(count).isEqualTo(1);
        verify(locationMapper).insert(ArgumentMatchers.argThat(w ->
                "L1".equals(w.getTier())
                && "青云城".equals(w.getName())
                && "300里".equals(w.getDistanceFromOrigin())
                && "传送阵".equals(w.getTransportation())
                && "正道联盟".equals(w.getFactionTerritory())
                && "云雾缭绕".equals(w.getVisualReference())
        ));
    }

    // ===== Phase 1.4: aiGenerateTimeline =====

    @Test
    @DisplayName("aiGenerateTimeline — 重新生成前清理 + 填充 locationId/plantedInUnitId/payoffInUnitId")
    void aiGenerateTimeline_clearsOldAndPopulatesAllFields() {
        Map<String, Object> parsed = Map.of(
                "events", List.of(Map.of("name", "启程", "time", "第一章",
                        "description", "主角出发", "characters", List.of("主角"),
                        "location_name", "青云城")),
                "foreshadowing", List.of(Map.of("description", "神秘戒指", "category", "道具",
                        "planted_in", "1", "payoff_in", "10", "characters", List.of("主角"))));

        when(aiRouter.chatCompletion(any())).thenReturn(Map.of());
        when(parser.extractText(any())).thenReturn("{}");
        when(parser.parseJson(any())).thenReturn(parsed);

        // Mock location lookup
        WorldLocation loc = new WorldLocation(); loc.setId(5L); loc.setName("青云城");
        when(locationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(loc);

        // Mock unit lookup for parseUnitRef
        ContentUnit unit1 = new ContentUnit(); unit1.setId(10L); unit1.setDisplayNo(1);
        when(unitMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unit1);

        doAnswer(inv -> { StoryTimeline s = inv.getArgument(0); return 1; })
                .when(timelineMapper).insert(any(StoryTimeline.class));
        doAnswer(inv -> { ForeshadowingItem f = inv.getArgument(0); f.setId(1L); return 1; })
                .when(foreshadowMapper).insert(any(ForeshadowingItem.class));

        Map<String, Object> result = service.aiGenerateTimeline(1L, "上下文");

        assertThat(result.get("events_created")).isEqualTo(1);
        assertThat(result.get("foreshadows_created")).isEqualTo(1);
        // Verify old data cleared
        verify(timelineMapper).delete(any(LambdaQueryWrapper.class));
        verify(foreshadowMapper).delete(any(LambdaQueryWrapper.class));
        // Verify locationId set on timeline
        verify(timelineMapper).insert(ArgumentMatchers.argThat(st ->
                st.getLocationId() != null && st.getLocationId() == 5L));
        // Verify plantedInUnitId/payoffInUnitId set on foreshadowing
        verify(foreshadowMapper).insert(ArgumentMatchers.argThat(fi ->
                fi.getPlantedInUnitId() != null && fi.getPayoffInUnitId() != null));
    }
}
