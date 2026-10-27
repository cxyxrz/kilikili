package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.Account;
import com.kilikili.web.entity.ConversationItem;
import com.kilikili.web.entity.PrivateMessageItem;
import com.kilikili.web.mapper.AccountMapper;
import com.kilikili.web.mapper.PrivateMessageMapper;
import com.kilikili.web.realtime.RealtimePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrivateMessageService {
    private static final Logger log = LoggerFactory.getLogger(PrivateMessageService.class);
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_CONVERSATIONS = 50;

    private final PrivateMessageMapper privateMessageMapper;
    private final AccountMapper accountMapper;
    private final StorageService storageService;
    private final RealtimePushService realtimePushService;

    public PrivateMessageService(PrivateMessageMapper privateMessageMapper, AccountMapper accountMapper,
                                 StorageService storageService, RealtimePushService realtimePushService) {
        this.privateMessageMapper = privateMessageMapper;
        this.accountMapper = accountMapper;
        this.storageService = storageService;
        this.realtimePushService = realtimePushService;
    }

    public Map<String, Object> conversations(long userId) {
        List<ConversationItem> items = privateMessageMapper.listConversations(userId, MAX_CONVERSATIONS).stream()
                .map(source -> new ConversationItem(source.peerId(), source.peerName(), avatarUrl(source.peerAvatar()),
                        source.lastContent(), source.lastAt(), source.unread() == null ? 0 : source.unread()))
                .toList();
        return Map.of("items", items);
    }

    public Map<String, Object> thread(long userId, long peerId, int page, int pageSize) {
        if (peerId == userId) throw new BusinessException("不能给自己发私信");
        Account peer = accountMapper.findById(peerId);
        if (peer == null) throw new BusinessException("用户不存在");
        int offset = (page - 1) * pageSize;
        List<PrivateMessageItem> items = privateMessageMapper.listThread(userId, peerId, pageSize, offset);
        long total = privateMessageMapper.countThread(userId, peerId);
        Map<String, Object> peerInfo = new HashMap<>();
        peerInfo.put("id", peer.id());
        peerInfo.put("nickname", peer.nickname());
        peerInfo.put("avatar", avatarUrl(peer.avatar()));
        Map<String, Object> result = new HashMap<>();
        result.put("peer", peerInfo);
        result.put("items", items);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("hasMore", (long) page * pageSize < total);
        return result;
    }

    @Transactional
    public Map<String, Object> send(long fromId, long toId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) throw new BusinessException("私信内容不能为空");
        if (trimmed.length() > MAX_CONTENT_LENGTH) throw new BusinessException("私信内容不能超过 500 字");
        if (fromId == toId) throw new BusinessException("不能给自己发私信");
        if (accountMapper.findById(toId) == null) throw new BusinessException("用户不存在");
        Account sender = accountMapper.findById(fromId);
        privateMessageMapper.insert(fromId, toId, trimmed);
        long unread = privateMessageMapper.countUnread(toId);
        realtimePushService.pushAfterCommit(toId, Map.of(
                "kind", "MESSAGE",
                "fromId", fromId,
                "fromName", sender == null ? "用户" : sender.nickname(),
                "content", trimmed,
                "unread", unread));
        log.info("私信已发送: fromId={}, toId={}, contentLength={}", fromId, toId, trimmed.length());
        return Map.of("sent", true);
    }

    @Transactional
    public Map<String, Object> markRead(long userId, long peerId) {
        privateMessageMapper.markRead(userId, peerId);
        return Map.of("unread", privateMessageMapper.countUnread(userId));
    }

    public long unreadCount(long userId) { return privateMessageMapper.countUnread(userId); }

    private String avatarUrl(String avatar) {
        return avatar == null || avatar.isBlank() ? null : storageService.presignedReadUrl(avatar);
    }
}
