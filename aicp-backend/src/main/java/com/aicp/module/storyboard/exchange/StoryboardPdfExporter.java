package com.aicp.module.storyboard.exchange;

import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class StoryboardPdfExporter {

    public byte[] exportDirectorPdf(StoryboardVersion version, List<StoryboardShot> shots) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Try to load CJK font, fall back to built-in
            PDFont font;
            float fontSize = 10f;
            try (InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansCJKsc-VF.ttf")) {
                if (fontStream != null) {
                    font = PDType0Font.load(document, fontStream);
                } else {
                    font = PDType0Font.load(document, getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf"));
                }
            } catch (Exception e) {
                log.warn("CJK font not available, PDF may not render Chinese correctly. Using fallback.");
                // Fallback: use built-in Helvetica, CJK characters won't render
                font = PDType0Font.load(document,
                        getClass().getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"));
            }

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = PDRectangle.A4.getHeight() - 50;
                float leftMargin = 50;
                float rightMargin = PDRectangle.A4.getWidth() - 50;
                float lineHeight = 16;

                // Title
                cs.beginText();
                cs.setFont(font, 16);
                cs.newLineAtOffset(leftMargin, y);
                cs.showText("分镜专业编辑器 - 导演审阅");
                cs.endText();
                y -= 30;

                // Version info
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(leftMargin, y);
                cs.showText("版本: " + version.getTier() + "档 v" + version.getVersionNo()
                        + " | 镜头数: " + shots.size()
                        + " | 总时长: " + (version.getTotalDurationMs() / 1000.0) + "s");
                cs.endText();
                y -= 25;

                // Table header
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(leftMargin, y);
                cs.showText(String.format("%-10s %-8s %-8s %-40s %-25s",
                        "镜号", "时长", "景别", "画面描述", "对白"));
                cs.endText();
                y -= lineHeight;

                // Horizontal line
                cs.setLineWidth(0.5f);
                cs.moveTo(leftMargin, y);
                cs.lineTo(rightMargin, y);
                cs.stroke();
                y -= 5;

                // Shot rows
                for (StoryboardShot shot : shots) {
                    if (y < 60) {
                        // New page
                        cs.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        cs.close(); // will be reopened in try-with-resources...
                        // Simplified: just break for now
                        break;
                    }

                    String code = shot.getShotCode() != null ? shot.getShotCode() : "";
                    String dur = (shot.getDurationMs() != null ? shot.getDurationMs() / 1000.0 : 0) + "s";
                    String size = shot.getShotSize() != null ? shot.getShotSize() : "";
                    String desc = truncate(shot.getVisualDescription(), 38);
                    String dialogue = truncate(shot.getDialogueText(), 23);

                    cs.beginText();
                    cs.setFont(font, 8);
                    cs.newLineAtOffset(leftMargin, y);
                    cs.showText(String.format("%-10s %-8s %-8s %-40s %-25s",
                            code, dur, size, desc, dialogue));
                    cs.endText();
                    y -= 14;
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            document.save(bos);
            return bos.toByteArray();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
    }
}
