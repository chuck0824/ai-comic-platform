package com.aicp.module.delivery;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CMX3600 EDL 格式写入器。
 * 仅记录镜头顺序和入出点。变速、转场、多轨混音不在 EDL 中保留。
 */
@Component
public class EdlWriter implements ExchangeWriter {

    @Override
    public String fileName() { return "timeline.edl"; }

    @Override
    public byte[] write(DeliveryManifestService.DeliveryManifestView manifest) {
        StringBuilder sb = new StringBuilder();
        sb.append("TITLE: Canvas Delivery v").append(manifest.revision()).append("\n");
        sb.append("FCM: NON-DROP FRAME\n\n");

        int editNo = 1;
        for (var item : manifest.items()) {
            String reel = String.format("SHOT_%03d", item.sortOrder() + 1);
            int durationFrames = item.durationFrames() > 0 ? item.durationFrames() : 120; // default 5s@24fps

            sb.append(String.format("%03d  %-8s V    C        %s %s %s %s\n",
                    editNo, reel,
                    formatTc(0, item.fps()), formatTc(durationFrames, item.fps()),
                    formatTc(0, item.fps()), formatTc(durationFrames, item.fps())));

            sb.append(String.format("* FROM CLIP NAME: SHOT_%03d_identity.mp4\n", item.sortOrder() + 1));
            sb.append(String.format("* COMMENT: adopted at %s\n",
                    item.adoptedAt() != null ? item.adoptedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "N/A"));
            sb.append("\n");
            editNo++;
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String formatTc(int frames, int fps) {
        int secs = frames / fps;
        int f = frames % fps;
        int mins = secs / 60;
        int s = secs % 60;
        int hrs = mins / 60;
        int m = mins % 60;
        return String.format("%02d:%02d:%02d:%02d", hrs, m, s, f);
    }
}
