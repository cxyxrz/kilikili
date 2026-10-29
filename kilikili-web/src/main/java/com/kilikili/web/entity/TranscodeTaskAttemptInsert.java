package com.kilikili.web.entity;

/** 插入尝试记录时只需要任务号/尝试序号，id 由数据库回填 */
public class TranscodeTaskAttemptInsert {
    private Long id;
    private final long taskId;
    private final long videoId;
    private final int attemptNo;
    private final String status;

    public TranscodeTaskAttemptInsert(long taskId, long videoId, int attemptNo, String status) {
        this.taskId = taskId;
        this.videoId = videoId;
        this.attemptNo = attemptNo;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getTaskId() {
        return taskId;
    }

    public long getVideoId() {
        return videoId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getStatus() {
        return status;
    }
}
