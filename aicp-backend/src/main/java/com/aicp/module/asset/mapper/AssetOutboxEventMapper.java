package com.aicp.module.asset.mapper;

import com.aicp.module.asset.entity.AssetOutboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetOutboxEventMapper extends BaseMapper<AssetOutboxEvent> {
}
