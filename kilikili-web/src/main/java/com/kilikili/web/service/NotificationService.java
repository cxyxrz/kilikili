package com.kilikili.web.service;

import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.NotificationItem;
import com.kilikili.web.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final StorageService storageService;

    public NotificationService(NotificationMapper notificationMapper, StorageService storageService) {
        this.notificationMapper = notificationMapper;
        this.storageService = storageService;
    }

    @Transactional
    public void create(long recipientId, long actorId, String type, String content, Long videoId) {
        if (recipientId == actorId) return;
        notificationMapper.insert(recipientId, actorId, type, content.length() > 500 ? content.substring(0, 500) : content, videoId);
    }

    public Map<String, Object> list(long userId, String type, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<NotificationItem> items = notificationMapper.list(userId, type, pageSize, offset).stream().map(source ->
                new NotificationItem(source.id(), source.type(), source.content(), source.videoId(), source.readAt(), source.createdAt(),
                        source.actorName(), source.actorAvatar() == null || source.actorAvatar().isBlank() ? null : storageService.presignedReadUrl(source.actorAvatar()))).toList();
        long total = notificationMapper.count(userId, type);
        return Map.of("items", items, "page", page, "pageSize", pageSize, "total", total, "hasMore", (long) page * pageSize < total);
    }

    public long unreadCount(long userId) { return notificationMapper.countUnread(userId); }

    @Transactional
    public void markAllRead(long userId) { notificationMapper.markAllRead(userId); }

    @Transactional
    public void delete(long userId, long notificationId) { notificationMapper.delete(userId, notificationId); }
}
