package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.common.setting.SystemSettingService;
import com.kilikili.common.storage.StorageService;
import com.kilikili.web.entity.LiveMessage;
import com.kilikili.web.entity.LiveMessageInsert;
import com.kilikili.web.entity.LiveRoom;
import com.kilikili.web.mapper.LiveMessageMapper;
import com.kilikili.web.mapper.LiveRoomMapper;
import com.kilikili.web.realtime.LiveWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveService {
    private static final Logger log = LoggerFactory.getLogger(LiveService.class);
    private static final int CHAT_MAX_LENGTH = 100;
    private static final int RECENT_MESSAGE_LIMIT = 50;
    private static final int ROOM_LIST_LIMIT = 30;

    private final LiveRoomMapper liveRoomMapper;
    private final LiveMessageMapper liveMessageMapper;
    private final StorageService storageService;
    private final SystemSettingService systemSettingService;
    private final LiveWebSocketHandler liveWebSocketHandler;
    private final String rtmpBaseUrl;
    private final String hlsBaseUrl;
    private final Path hlsDir;

    public LiveService(LiveRoomMapper liveRoomMapper, LiveMessageMapper liveMessageMapper, StorageService storageService,
                       SystemSettingService systemSettingService, LiveWebSocketHandler liveWebSocketHandler,
                       @Value("${kilikili.live.rtmp-base-url:rtmp://localhost:1935/live}") String rtmpBaseUrl,
                       @Value("${kilikili.live.hls-base-url:http://127.0.0.1:8082}") String hlsBaseUrl,
                       @Value("${kilikili.live.hls-dir:../deploy/srs/media}") String hlsDir) {
        this.liveRoomMapper = liveRoomMapper;
        this.liveMessageMapper = liveMessageMapper;
        this.storageService = storageService;
        this.systemSettingService = systemSettingService;
        this.liveWebSocketHandler = liveWebSocketHandler;
        this.rtmpBaseUrl = rtmpBaseUrl.endsWith("/") ? rtmpBaseUrl.substring(0, rtmpBaseUrl.length() - 1) : rtmpBaseUrl;
        this.hlsBaseUrl = hlsBaseUrl.endsWith("/") ? hlsBaseUrl.substring(0, hlsBaseUrl.length() - 1) : hlsBaseUrl;
        // SRS 的 HLS 输出目录，后端直接读盘代理，避免暴露推流密钥
        this.hlsDir = Path.of(hlsDir).toAbsolutePath().normalize();
    }

    public List<Map<String, Object>> rooms() {
        return liveRoomMapper.list(ROOM_LIST_LIMIT).stream().map(room -> view(room, 0)).toList();
    }

    public Map<String, Object> mine(long userId) {
        LiveRoom room = liveRoomMapper.findLatestByUser(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("room", room == null ? null : view(room, userId));
        return result;
    }

    public Map<String, Object> detail(long requesterId, long roomId) {
        LiveRoom room = requireRoom(roomId);
        Map<String, Object> result = new HashMap<>();
        result.put("room", view(room, requesterId));
        result.put("messages", messages(roomId));
        return result;
    }

    /**
     * 创建或复用直播间：待开播的房间直接更新标题/封面，已结束的则生成新的推流密钥。
     */
    @Transactional
    public Map<String, Object> create(long userId, String title, MultipartFile cover) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) throw new BusinessException("请填写直播标题");
        if (trimmed.length() > 80) throw new BusinessException("直播标题不能超过 80 字");
        LiveRoom latest = liveRoomMapper.findLatestByUser(userId);
        if (latest != null && "LIVING".equals(latest.status())) throw new BusinessException("你还有正在直播的房间，请先下播");
        String coverObjectKey = latest == null ? null : latest.coverObjectKey();
        if (cover != null && !cover.isEmpty()) {
            String contentType = cover.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) throw new BusinessException("封面仅支持图片文件");
            coverObjectKey = storageService.uploadCover(cover, userId);
        }
        if (latest != null && "READY".equals(latest.status())) {
            liveRoomMapper.updateRoomInfo(latest.id(), trimmed, coverObjectKey);
            log.info("直播间信息已更新: roomId={}, userId={}, title={}", latest.id(), userId, trimmed);
            return detail(userId, latest.id());
        }
        String streamKey = UUID.randomUUID().toString().replace("-", "");
        liveRoomMapper.insert(userId, trimmed, coverObjectKey, streamKey);
        LiveRoom created = liveRoomMapper.findByStreamKey(streamKey);
        if (created == null) throw new BusinessException("直播间创建失败，请重试");
        log.info("直播间已创建: roomId={}, userId={}, streamKey={}", created.id(), userId, streamKey);
        return detail(userId, created.id());
    }

    @Transactional
    public Map<String, Object> start(long userId, long roomId) {
        LiveRoom room = requireOwner(userId, roomId);
        if ("ENDED".equals(room.status())) throw new BusinessException("直播已结束，请重新创建直播间");
        if (!"LIVING".equals(room.status())) {
            liveRoomMapper.markLiving(roomId);
            log.info("直播间已手动开播: roomId={}, userId={}", roomId, userId);
        }
        pushStatus(roomId, "LIVING");
        return detail(userId, roomId);
    }

    @Transactional
    public Map<String, Object> stop(long userId, long roomId) {
        LiveRoom room = requireOwner(userId, roomId);
        if (!"ENDED".equals(room.status())) {
            liveRoomMapper.markEnded(roomId);
            log.info("直播间已下播: roomId={}, userId={}", roomId, userId);
        }
        pushStatus(roomId, "ENDED");
        return detail(userId, roomId);
    }

    public List<Map<String, Object>> messages(long roomId) {
        List<LiveMessage> recent = liveMessageMapper.listRecent(roomId, RECENT_MESSAGE_LIMIT);
        List<Map<String, Object>> items = new ArrayList<>(recent.size());
        // 数据库按倒序取最近 N 条，展示时恢复正序
        for (int index = recent.size() - 1; index >= 0; index--) {
            items.add(messageView(recent.get(index)));
        }
        return items;
    }

    @Transactional
    public Map<String, Object> sendMessage(long userId, long roomId, String content) {
        systemSettingService.requireEnabled(SystemSettingService.DANMAKU_ENABLED, "弹幕功能已关闭");
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) throw new BusinessException("弹幕内容不能为空");
        if (trimmed.length() > CHAT_MAX_LENGTH) throw new BusinessException("弹幕不能超过 100 字");
        LiveRoom room = requireRoom(roomId);
        if (!"LIVING".equals(room.status())) throw new BusinessException("直播未开始，暂不能发送弹幕");
        LiveMessageInsert insert = new LiveMessageInsert(roomId, userId, "CHAT", trimmed);
        liveMessageMapper.insert(insert);
        liveRoomMapper.increaseDanmaku(roomId);
        LiveMessage message = liveMessageMapper.findById(insert.getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "LIVE_DANMAKU");
        payload.put("roomId", roomId);
        payload.put("message", message == null ? Map.of() : messageView(message));
        liveWebSocketHandler.broadcast(roomId, payload);
        log.info("直播弹幕已发布: roomId={}, userId={}, messageId={}", roomId, userId, insert.getId());
        return message == null ? Map.of() : messageView(message);
    }

    /** 代理 SRS 的 HLS 文件，并在 m3u8 中去掉推流密钥前缀，避免密钥泄露 */
    public byte[] hls(long roomId, String file) {
        LiveRoom room = requireRoom(roomId);
        if (!"LIVING".equals(room.status())) throw new BusinessException("直播未开始");
        String fileName;
        if ("index.m3u8".equals(file)) {
            fileName = room.streamKey() + ".m3u8";
        } else if (file.matches("\\d+\\.ts")) {
            fileName = room.streamKey() + "-" + file;
        } else {
            throw new BusinessException("非法的直播文件");
        }
        Path target = hlsDir.resolve("live").resolve(fileName).normalize();
        if (!target.startsWith(hlsDir)) throw new BusinessException("非法的直播文件");
        try {
            byte[] body = Files.readAllBytes(target);
            if ("index.m3u8".equals(file)) {
                // 播放列表中的切片名含推流密钥前缀，下发前去掉以隐藏密钥
                String playlist = new String(body, StandardCharsets.UTF_8).replace(room.streamKey() + "-", "");
                return playlist.getBytes(StandardCharsets.UTF_8);
            }
            return body;
        } catch (IOException exception) {
            log.warn("读取直播 HLS 文件失败: roomId={}, file={}, path={}", roomId, file, target);
            throw new BusinessException("直播流暂不可用，请确认主播正在推流");
        }
    }

    /** SRS on_publish 回调：校验推流密钥并标记开播，返回 0 表示允许推流 */
    @Transactional
    public int onPublish(Map<String, Object> payload) {
        String streamKey = asText(payload.get("stream"));
        LiveRoom room = streamKey == null ? null : liveRoomMapper.findByStreamKey(streamKey);
        if (room == null) {
            log.warn("SRS 推流被拒绝: 未知推流密钥 stream={}", streamKey);
            return 1;
        }
        liveRoomMapper.markLiving(room.id());
        log.info("SRS 推流已接入: roomId={}, streamKey={}", room.id(), streamKey);
        pushStatus(room.id(), "LIVING");
        return 0;
    }

    /** SRS on_unpublish 回调：推流断开后标记下播 */
    @Transactional
    public int onUnpublish(Map<String, Object> payload) {
        String streamKey = asText(payload.get("stream"));
        LiveRoom room = streamKey == null ? null : liveRoomMapper.findByStreamKey(streamKey);
        if (room == null) return 0;
        liveRoomMapper.markEnded(room.id());
        log.info("SRS 推流已断开: roomId={}, streamKey={}", room.id(), streamKey);
        pushStatus(room.id(), "ENDED");
        return 0;
    }

    private LiveRoom requireRoom(long roomId) {
        LiveRoom room = liveRoomMapper.findById(roomId);
        if (room == null) throw new BusinessException("直播间不存在");
        return room;
    }

    private LiveRoom requireOwner(long userId, long roomId) {
        LiveRoom room = requireRoom(roomId);
        if (room.userId() != userId) throw new BusinessException("无权操作此直播间");
        return room;
    }

    /** 转为前端视图；推流地址与密钥只返回给主播本人 */
    private Map<String, Object> view(LiveRoom room, long requesterId) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", room.id());
        item.put("userId", room.userId());
        item.put("title", room.title());
        item.put("status", room.status());
        item.put("viewerCount", liveWebSocketHandler.viewerCount(room.id()));
        item.put("danmakuCount", room.danmakuCount() == null ? 0 : room.danmakuCount());
        item.put("authorName", room.authorName());
        item.put("authorAvatar", avatarUrl(room.authorAvatar()));
        item.put("coverUrl", coverUrl(room.coverObjectKey()));
        item.put("startedAt", room.startedAt());
        item.put("endedAt", room.endedAt());
        item.put("createdAt", room.createdAt());
        item.put("playUrl", "LIVING".equals(room.status()) ? "/api/live/rooms/" + room.id() + "/hls/index.m3u8" : null);
        if (requesterId > 0 && room.userId() == requesterId) {
            item.put("streamKey", room.streamKey());
            item.put("pushUrl", rtmpBaseUrl + "/" + room.streamKey());
            item.put("hlsUrl", hlsBaseUrl + "/live/" + room.streamKey() + ".m3u8");
        }
        return item;
    }

    private Map<String, Object> messageView(LiveMessage message) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", message.id());
        item.put("roomId", message.roomId());
        item.put("userId", message.userId());
        item.put("type", message.type());
        item.put("content", message.content());
        item.put("createdAt", message.createdAt());
        item.put("nickname", message.nickname() == null ? "观众" : message.nickname());
        item.put("avatar", avatarUrl(message.avatar()));
        return item;
    }

    private void pushStatus(long roomId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "LIVE_STATUS");
        payload.put("roomId", roomId);
        payload.put("status", status);
        payload.put("viewerCount", liveWebSocketHandler.viewerCount(roomId));
        // 事务提交后再推送，避免客户端收到开播事件时数据库仍显示旧状态
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { liveWebSocketHandler.broadcast(roomId, payload); }
            });
            return;
        }
        liveWebSocketHandler.broadcast(roomId, payload);
    }

    private String asText(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String avatarUrl(String avatar) {
        return avatar == null || avatar.isBlank() ? null : storageService.presignedReadUrl(avatar);
    }

    private String coverUrl(String coverObjectKey) {
        return coverObjectKey == null || coverObjectKey.isBlank() ? null : storageService.presignedReadUrl(coverObjectKey);
    }
}
