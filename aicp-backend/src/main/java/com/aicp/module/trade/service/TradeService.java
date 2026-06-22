package com.aicp.module.trade.service;

import com.aicp.module.trade.entity.*;
import com.aicp.module.trade.mapper.*;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final OrderMapper orderMapper;
    private final ScriptMapper scriptMapper;

    public Page<Script> searchMarket(int page, int pageSize, String keyword, String genre, String sort) {
        LambdaQueryWrapper<Script> query = new LambdaQueryWrapper<>();
        query.eq(Script::getStatus, "listed");
        if (genre != null) query.eq(Script::getGenreTag, genre);
        if (keyword != null) query.like(Script::getTitle, keyword);
        query.orderByDesc("sales_count".equals(sort) ? Script::getSalesCount : Script::getUpdatedAt);
        return scriptMapper.selectPage(new Page<>(page, pageSize), query);
    }

    public Order createOrder(Map<String, Object> body) {
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis());
        order.setBuyerId(1L); // TODO: from auth
        order.setScriptId(toLong(body.get("script_id")));
        order.setLicenseType((String) body.getOrDefault("license_type", "normal"));
        order.setAmount(new BigDecimal(String.valueOf(body.getOrDefault("amount", "0"))));
        order.setSellerId(toLong(body.get("seller_id")));
        order.setStatus("pending");
        order.setExpireAt(LocalDateTime.now().plusMinutes(30));
        orderMapper.insert(order);
        return order;
    }

    public Order getOrder(Long id) { return orderMapper.selectById(id); }

    public List<Order> getOrders(Long buyerId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getBuyerId, buyerId).orderByDesc(Order::getCreatedAt));
    }

    public void payOrder(Long id, String method) {
        Order order = orderMapper.selectById(id);
        if (order != null) {
            order.setStatus("paid");
            order.setPaymentMethod(method);
            order.setPaidAt(LocalDateTime.now());
            orderMapper.updateById(order);
        }
    }

    public Map<String, Object> getSales() {
        return Map.of("total_revenue", 0, "total_orders", 0, "scripts_sold", 0);
    }

    public Map<String, Object> getEarnings() {
        return Map.of("balance", 0, "total_earned", 0, "total_withdrawn", 0);
    }

    public Map<String, Object> withdraw(Map<String, Object> body) {
        return Map.of("withdraw_id", "WD_" + System.currentTimeMillis(), "status", "pending");
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? null : Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }
}
