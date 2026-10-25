package com.kilikili.web.entity;

/**
 * 新建转码任务的可变参数对象，用于 MyBatis 回填自增主键。
 */
public class TranscodeTaskInsert {
    private Long id;
    private long videoId;
    private int maxAttempts;

    public TranscodeTaskInsert(long videoId, int maxAttempts) {
        this.videoId = videoId;
        this.maxAttempts = maxAttempts;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getVideoId() { return videoId; }
    public void setVideoId(long videoId) { this.videoId = videoId; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
}
