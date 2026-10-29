package com.kilikili.web.entity;

/**
 * 直播弹幕写入参数，用于 MyBatis 回填自增主键。
 */
public class LiveMessageInsert {
    private Long id;
    private final long roomId;
    private final long userId;
    private final String type;
    private final String content;

    public LiveMessageInsert(long roomId, long userId, String type, String content) {
        this.roomId = roomId;
        this.userId = userId;
        this.type = type;
        this.content = content;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getRoomId() { return roomId; }
    public long getUserId() { return userId; }
    public String getType() { return type; }
    public String getContent() { return content; }
}
