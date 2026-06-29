package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("LWorld_LLocations")
public class WorldLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name; private String tier; private String description; private Long parentLocationId;
    private String areaType; private String distanceFromOrigin; private String transportation;
    private String factionTerritory; private String visualReference;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
