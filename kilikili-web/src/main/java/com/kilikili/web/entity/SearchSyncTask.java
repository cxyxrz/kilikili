package com.kilikili.web.entity;

/** 搜索索引同步补偿任务（Outbox 表 search_sync_task 的一行） */
public record SearchSyncTask(long id, long videoId, String operation, int attempts) {
}
