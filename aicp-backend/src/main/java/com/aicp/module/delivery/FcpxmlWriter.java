package com.aicp.module.delivery;

import org.springframework.stereotype.Component;

/**
 * FCPXML 1.9 格式写入器。
 * 每个 ShotAdoption → 一个 asset-clip，按 sort_order 排列。
 * 媒体路径使用相对路径 media/SHOT_{n}.{ext}。
 */
@Component
public class FcpxmlWriter implements ExchangeWriter {

    private static final String FCPXML_VERSION = "1.9";

    @Override
    public String fileName() { return "timeline.fcpxml"; }

    @Override
    public byte[] write(DeliveryManifestService.DeliveryManifestView manifest) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE fcpxml>\n\n");
        sb.append("<fcpxml version=\"").append(FCPXML_VERSION).append("\">\n");
        sb.append("  <resources>\n");

        int fps = 24;
        for (int i = 0; i < manifest.items().size(); i++) {
            var item = manifest.items().get(i);
            fps = item.fps() > 0 ? item.fps() : 24;
            sb.append("    <asset id=\"r").append(i + 1)
              .append("\" name=\"SHOT_").append(String.format("%03d", i + 1))
              .append("_identity\" src=\"media/SHOT_").append(String.format("%03d", i + 1))
              .append(".mp4\" start=\"0s\" duration=\"")
              .append(item.durationFrames()).append("/").append(fps).append("s\" format=\"r2\" />\n");
        }

        sb.append("    <format id=\"r2\" name=\"FFVideoFormat1080p").append(fps)
          .append("\" width=\"1920\" height=\"1080\" />\n");
        sb.append("  </resources>\n");

        int totalFrames = manifest.items().stream().mapToInt(DeliveryManifestService.ItemView::durationFrames).sum();
        sb.append("  <library>\n");
        sb.append("    <event name=\"CanvasDelivery\">\n");
        sb.append("      <project name=\"Delivery_v").append(manifest.revision()).append("\">\n");
        sb.append("        <sequence format=\"r2\" duration=\"").append(totalFrames)
          .append("/").append(fps).append("s\" tcStart=\"0s\" tcFormat=\"NDF\">\n");
        sb.append("          <spine>\n");

        int offsetFrames = 0;
        for (int i = 0; i < manifest.items().size(); i++) {
            var item = manifest.items().get(i);
            sb.append("            <asset-clip ref=\"r").append(i + 1)
              .append("\" offset=\"").append(offsetFrames).append("/").append(fps)
              .append("s\" name=\"SHOT_").append(String.format("%03d", i + 1))
              .append("\" start=\"0s\" duration=\"").append(item.durationFrames())
              .append("/").append(fps).append("s\" />\n");
            offsetFrames += item.durationFrames();
        }

        sb.append("          </spine>\n");
        sb.append("        </sequence>\n");
        sb.append("      </project>\n");
        sb.append("    </event>\n");
        sb.append("  </library>\n");
        sb.append("</fcpxml>\n");

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
