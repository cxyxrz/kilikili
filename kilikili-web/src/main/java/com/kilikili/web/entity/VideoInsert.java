package com.kilikili.web.entity;

/**
 * 新建视频的可变参数对象，用于 MyBatis 回填自增主键。
 */
public class VideoInsert {
    private Long id;
    private long authorId;
    private String title;
    private String description;
    private String objectKey;
    private String coverObjectKey;
    private long categoryId;
    private int durationSeconds;
    private String creationDeclaration;
    private String status;

    public VideoInsert(long authorId, String title, String description, String objectKey, String coverObjectKey,
                       long categoryId, int durationSeconds, String creationDeclaration, String status) {
        this.authorId = authorId;
        this.title = title;
        this.description = description;
        this.objectKey = objectKey;
        this.coverObjectKey = coverObjectKey;
        this.categoryId = categoryId;
        this.durationSeconds = durationSeconds;
        this.creationDeclaration = creationDeclaration;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getObjectKey() { return objectKey; }
    public String getCoverObjectKey() { return coverObjectKey; }
    public long getCategoryId() { return categoryId; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getCreationDeclaration() { return creationDeclaration; }
    public String getStatus() { return status; }
}
