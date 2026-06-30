package com.aicp.module.storyboard.exchange;

import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoryboardWorkbookExporter {

    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardEmotionSegmentMapper emotionSegmentMapper;
    private final StoryboardPromptTemplateMapper promptTemplateMapper;
    private final StoryboardCreativeRuleMapper creativeRuleMapper;
    private final StoryboardCharacterVisualMapper characterVisualMapper;
    private final StoryboardShotVisualBindingMapper visualBindingMapper;
    private final StoryboardVersionMapper versionMapper;

    public byte[] exportFullWorkbook(Long versionId, Long storyboardId) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var shots = shotMapper.selectList(
                    new LambdaQueryWrapper<StoryboardShot>()
                            .eq(StoryboardShot::getVersionId, versionId)
                            .orderByAsc(StoryboardShot::getSortOrder));
            var scenes = sceneMapper.selectList(
                    new LambdaQueryWrapper<StoryboardScene>()
                            .eq(StoryboardScene::getVersionId, versionId)
                            .orderByAsc(StoryboardScene::getSortOrder));

            // Sheet 1: Shots
            createShotSheet(workbook, shots, scenes);

            // Sheet 2-7: Professional modules
            var emotions = emotionSegmentMapper.selectList(
                    new LambdaQueryWrapper<StoryboardEmotionSegment>()
                            .eq(StoryboardEmotionSegment::getVersionId, versionId));
            createEmotionSheet(workbook, emotions);

            var templates = promptTemplateMapper.selectList(
                    new LambdaQueryWrapper<StoryboardPromptTemplate>()
                            .eq(StoryboardPromptTemplate::getVersionId, versionId));
            createPromptSheet(workbook, templates);

            var rules = creativeRuleMapper.selectList(
                    new LambdaQueryWrapper<StoryboardCreativeRule>()
                            .eq(StoryboardCreativeRule::getVersionId, versionId));
            createRulesSheet(workbook, rules);

            var consistencyRules = creativeRuleMapper.selectList(
                    new LambdaQueryWrapper<StoryboardCreativeRule>()
                            .eq(StoryboardCreativeRule::getVersionId, versionId)
                            .eq(StoryboardCreativeRule::getRuleType, "consistency"));
            createConsistencySheet(workbook, consistencyRules);

            var visuals = characterVisualMapper.selectList(
                    new LambdaQueryWrapper<StoryboardCharacterVisual>()
                            .eq(StoryboardCharacterVisual::getVersionId, versionId));
            createVisualsSheet(workbook, visuals);

            var bindings = visualBindingMapper.selectList(
                    new LambdaQueryWrapper<StoryboardShotVisualBinding>()
                            .eq(StoryboardShotVisualBinding::getVersionId, versionId));
            createBindingsSheet(workbook, bindings, visuals, shots);

            // Hidden schema sheet
            createHiddenSchemaSheet(workbook, versionId, storyboardId, scenes, shots);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private void createShotSheet(Workbook workbook, List<StoryboardShot> shots, List<StoryboardScene> scenes) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_SHOTS);
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row header = sheet.createRow(0);
        List<String> headers = StoryboardWorkbookSchema.SHOT_HEADERS;
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < shots.size(); i++) {
            StoryboardShot shot = shots.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(shot.getShotCode());
            row.createCell(1).setCellValue(shot.getDurationMs() != null ? shot.getDurationMs() / 1000.0 : 0);
            row.createCell(2).setCellValue(shot.getShotSize());
            row.createCell(3).setCellValue(shot.getVisualDescription());
            row.createCell(4).setCellValue(shot.getLightingAtmosphere());
            row.createCell(5).setCellValue(shot.getCharacterAction());
            row.createCell(6).setCellValue(shot.getEmotionDescription());
            row.createCell(7).setCellValue(shot.getDialogueText());
            row.createCell(8).setCellValue(shot.getSceneTagsJson());
            row.createCell(9).setCellValue(shot.getSoundEffect());
            row.createCell(10).setCellValue(shot.getReferenceText());
            row.createCell(11).setCellValue(shot.getImagePrompt());
            row.createCell(12).setCellValue(shot.getVideoMotionPrompt());
        }

        sheet.setAutoFilter(new CellRangeAddress(0, shots.size(), 0, 12));
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < 13; i++) sheet.setColumnWidth(i, 20 * 256);
    }

    private void createEmotionSheet(Workbook workbook, List<StoryboardEmotionSegment> segments) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_EMOTION);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(0);
        String[] cols = {"情绪类型", "镜头范围", "强度", "核心表达"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < segments.size(); i++) {
            var s = segments.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(s.getEmotionType());
            row.createCell(1).setCellValue(s.getShotRange());
            row.createCell(2).setCellValue(s.getIntensity() != null ? s.getIntensity() : 0);
            row.createCell(3).setCellValue(s.getCoreExpression());
        }
        sheet.createFreezePane(0, 1);
    }

    private void createPromptSheet(Workbook workbook, List<StoryboardPromptTemplate> templates) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_PROMPTS);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(0);
        String[] cols = {"模板编号", "情绪名", "镜头范围", "图片提示词", "视频提示词"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < templates.size(); i++) {
            var t = templates.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(t.getTemplateCode());
            row.createCell(1).setCellValue(t.getEmotionName());
            row.createCell(2).setCellValue(t.getShotRefsJson());
            row.createCell(3).setCellValue(t.getImagePrompt());
            row.createCell(4).setCellValue(t.getVideoMotionPrompt());
        }
        sheet.createFreezePane(0, 1);
    }

    private void createRulesSheet(Workbook workbook, List<StoryboardCreativeRule> rules) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_RULES);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(0);
        String[] cols = {"类型", "维度", "原则", "实施方案", "影响", "状态"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < rules.size(); i++) {
            var r = rules.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(r.getRuleType());
            row.createCell(1).setCellValue(r.getDimensionName());
            row.createCell(2).setCellValue(r.getPrinciple());
            row.createCell(3).setCellValue(r.getImplementationText());
            row.createCell(4).setCellValue(r.getEffectText());
            row.createCell(5).setCellValue(r.getStatus());
        }
        sheet.createFreezePane(0, 1);
    }

    private void createConsistencySheet(Workbook workbook, List<StoryboardCreativeRule> rules) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_CONSISTENCY);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(0);
        String[] cols = {"维度", "原则", "实施方案"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < rules.size(); i++) {
            var r = rules.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(r.getDimensionName());
            row.createCell(1).setCellValue(r.getPrinciple());
            row.createCell(2).setCellValue(r.getImplementationText());
        }
        sheet.createFreezePane(0, 1);
    }

    private void createVisualsSheet(Workbook workbook, List<StoryboardCharacterVisual> visuals) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_VISUALS);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(0);
        String[] cols = {"角色名", "核心识别", "日常造型", "任务造型", "表演锚点"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < visuals.size(); i++) {
            var v = visuals.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(v.getCharacterName());
            row.createCell(1).setCellValue(v.getCoreIdentity());
            row.createCell(2).setCellValue(v.getDailyLook());
            row.createCell(3).setCellValue(v.getTaskLook());
            row.createCell(4).setCellValue(v.getPerformanceAnchor());
        }
        sheet.createFreezePane(0, 1);
    }

    private void createBindingsSheet(Workbook workbook, List<StoryboardShotVisualBinding> bindings,
                                      List<StoryboardCharacterVisual> visuals, List<StoryboardShot> shots) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.SHEET_BINDINGS);
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row header = sheet.createRow(0);
        String[] cols = {"镜头", "角色", "应用说明", "防漂移要求"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < bindings.size(); i++) {
            var b = bindings.get(i);
            Row row = sheet.createRow(i + 1);
            String shotCode = shots.stream().filter(s -> s.getId().equals(b.getShotId()))
                    .map(StoryboardShot::getShotCode).findFirst().orElse("");
            String charName = visuals.stream().filter(v -> v.getId().equals(b.getCharacterVisualId()))
                    .map(StoryboardCharacterVisual::getCharacterName).findFirst().orElse("");
            row.createCell(0).setCellValue(shotCode);
            row.createCell(1).setCellValue(charName);
            row.createCell(2).setCellValue(b.getApplicationNote());
            row.createCell(3).setCellValue(b.getAntiDriftRequirement());
        }
        sheet.createFreezePane(0, 1);
    }

    private void createHiddenSchemaSheet(Workbook workbook, Long versionId, Long storyboardId,
                                          List<StoryboardScene> scenes, List<StoryboardShot> shots) {
        Sheet sheet = workbook.createSheet(StoryboardWorkbookSchema.HIDDEN_SHEET);
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("schema_version");
        row.createCell(1).setCellValue(StoryboardWorkbookSchema.SCHEMA_VERSION);
        Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("storyboard_id");
        row2.createCell(1).setCellValue(storyboardId.toString());
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("version_id");
        row3.createCell(1).setCellValue(versionId.toString());
        // Hide this sheet
        workbook.setSheetHidden(workbook.getSheetIndex(sheet), true);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
