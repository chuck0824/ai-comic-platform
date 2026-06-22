package com.aicp.module.notify.service;

import com.aicp.module.notify.entity.Notification;
import com.aicp.module.notify.mapper.NotificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotificationMapper notificationMapper;

    public Page<Notification> getNotifications(Long userId, int page, int pageSize) {
        return notificationMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));
    }

    public void markRead(Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n != null) { n.setIsRead(1); notificationMapper.updateById(n); }
    }

    public void markAllRead(Long userId) {
        var list = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId).eq(Notification::getIsRead, 0));
        for (Notification n : list) {
            n.setIsRead(1); notificationMapper.updateById(n);
        }
    }

    public void createNotification(Long userId, String type, String title, String content, String actionUrl) {
        Notification n = new Notification();
        n.setUserId(userId); n.setType(type); n.setTitle(title);
        n.setContent(content); n.setActionUrl(actionUrl);
        notificationMapper.insert(n);
    }
}
