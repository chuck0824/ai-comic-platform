package com.aicp.module.storyboard.exchange;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
public class StoryboardWorkbookImporter {

    public WorkbookImportModel parse(InputStream inputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            WorkbookImportModel model = new WorkbookImportModel();
            DataFormatter formatter = new DataFormatter();

            // Verify required sheets
            Set<String> actualSheets = new HashSet<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                actualSheets.add(workbook.getSheetName(i));
            }
            model.sheetNames = new ArrayList<>(actualSheets);

            // Parse shots sheet
            Sheet shotSheet = workbook.getSheet(StoryboardWorkbookSchema.SHEET_SHOTS);
            if (shotSheet != null) {
                parseShots(shotSheet, formatter, model);
            }

            // Parse emotion segments
            Sheet emotionSheet = workbook.getSheet(StoryboardWorkbookSchema.SHEET_EMOTION);
            if (emotionSheet != null) {
                model.emotionSegments = parseEmotionSegments(emotionSheet, formatter);
            }

            return model;
        }
    }

    private void parseShots(Sheet sheet, DataFormatter formatter, WorkbookImportModel model) {
        List<ImportedShot> shots = new ArrayList<>();
        Map<Integer, List<ImportedShot>> sceneGroups = new LinkedHashMap<>();
        int currentScene = 0;

        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            String shotCode = formatter.formatCellValue(row.getCell(0)).trim();
            String durationStr = formatter.formatCellValue(row.getCell(1)).trim();

            if (shotCode.isEmpty() && durationStr.isEmpty()) continue;

            // Detect scene change from shot code pattern S01, S02, etc.
            if (!shotCode.isEmpty()) {
                int sceneNo = extractSceneNo(shotCode);
                if (sceneNo != currentScene) {
                    currentScene = sceneNo;
                    sceneGroups.putIfAbsent(currentScene, new ArrayList<>());
                }
            }
            if (currentScene == 0) currentScene = 1;
            sceneGroups.putIfAbsent(currentScene, new ArrayList<>());

            ImportedShot shot = new ImportedShot();
            shot.shotCode = shotCode;
            shot.durationMs = parseDurationMs(durationStr);
            shot.shotSize = formatter.formatCellValue(row.getCell(2)).trim();
            shot.visualDescription = formatter.formatCellValue(row.getCell(3)).trim();
            shot.lightingAtmosphere = formatter.formatCellValue(row.getCell(4)).trim();
            shot.characterAction = formatter.formatCellValue(row.getCell(5)).trim();
            shot.emotionDescription = formatter.formatCellValue(row.getCell(6)).trim();
            shot.dialogueText = formatter.formatCellValue(row.getCell(7)).trim();
            shot.sceneTags = formatter.formatCellValue(row.getCell(8)).trim();
            shot.soundEffect = formatter.formatCellValue(row.getCell(9)).trim();
            shot.referenceText = formatter.formatCellValue(row.getCell(10)).trim();
            shot.imagePrompt = formatter.formatCellValue(row.getCell(11)).trim();
            shot.videoMotionPrompt = formatter.formatCellValue(row.getCell(12)).trim();
            shot.sceneNo = currentScene;

            shots.add(shot);
            sceneGroups.get(currentScene).add(shot);
        }

        model.shots = shots;
        model.sceneCount = sceneGroups.size();
        model.shotCount = shots.size();
        model.totalDurationMs = shots.stream().mapToLong(s -> s.durationMs).sum();
    }

    private List<Map<String, String>> parseEmotionSegments(Sheet sheet, DataFormatter formatter) {
        List<Map<String, String>> segments = new ArrayList<>();
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            String type = formatter.formatCellValue(row.getCell(0)).trim();
            if (type.isEmpty()) continue;
            Map<String, String> seg = new LinkedHashMap<>();
            seg.put("emotionType", type);
            seg.put("shotRange", formatter.formatCellValue(row.getCell(1)).trim());
            seg.put("intensity", formatter.formatCellValue(row.getCell(2)).trim());
            seg.put("coreExpression", row.getLastCellNum() > 3
                    ? formatter.formatCellValue(row.getCell(3)).trim() : "");
            segments.add(seg);
        }
        return segments;
    }

    private int extractSceneNo(String shotCode) {
        try {
            String num = shotCode.replaceAll("[^0-9].*", "");
            if (num.isEmpty()) num = shotCode.replaceAll("S", "").replaceAll("-.*", "");
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseDurationMs(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            String cleaned = value.replace("s", "").replace("S", "").trim();
            double seconds = Double.parseDouble(cleaned);
            return (long) (seconds * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ===== Model classes =====

    public static class WorkbookImportModel {
        public List<String> sheetNames = List.of();
        public List<ImportedShot> shots = List.of();
        public List<Map<String, String>> emotionSegments = List.of();
        public int sceneCount;
        public int shotCount;
        public long totalDurationMs;

        public String semanticDigest() {
            StringBuilder sb = new StringBuilder();
            sb.append("scenes:").append(sceneCount).append(";");
            sb.append("shots:").append(shotCount).append(";");
            sb.append("duration:").append(totalDurationMs).append(";");
            for (ImportedShot s : shots) {
                sb.append(s.shotCode).append("|").append(s.visualDescription).append("|").append(s.durationMs).append(";");
            }
            return sb.toString();
        }
    }

    public static class ImportedShot {
        public String shotCode;
        public long durationMs;
        public String shotSize;
        public String visualDescription;
        public String lightingAtmosphere;
        public String characterAction;
        public String emotionDescription;
        public String dialogueText;
        public String sceneTags;
        public String soundEffect;
        public String referenceText;
        public String imagePrompt;
        public String videoMotionPrompt;
        public int sceneNo;
    }
}
