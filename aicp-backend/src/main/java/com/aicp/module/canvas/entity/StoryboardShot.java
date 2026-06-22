package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("storyboard_shots")
public class StoryboardShot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long storyboardId;
    private Long projectId;
    private Integer shotNo;
    private Integer sceneNo;
    private Integer duration;
    private String shotSize;
    private String cameraMotion;
    private String visualDescription;
    private String characters;
    private String sceneAssetId;
    private String dialogue;
    private String imagePrompt;
    private String videoPrompt;
    private String keyframeStart;
    private String keyframeEnd;
    private String imageStatus;
    private String videoStatus;
    private String metadata;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
