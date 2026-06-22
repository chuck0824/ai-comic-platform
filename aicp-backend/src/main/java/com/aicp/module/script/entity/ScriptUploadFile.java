package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("script_upload_files")
public class ScriptUploadFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long userId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String storagePath;
    private String parseStatus;   // pending, parsing, completed, failed
    private String parseResult;
    private Integer episodeCount;
    private Integer totalWords;
    private String errorMsg;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
