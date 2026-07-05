package com.aicp.module.trade.mapper;

import com.aicp.module.trade.entity.TradeOutboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeOutboxEventMapper extends BaseMapper<TradeOutboxEvent> {
}
