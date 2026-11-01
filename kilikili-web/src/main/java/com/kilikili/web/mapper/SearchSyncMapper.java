package com.kilikili.web.mapper;

import com.kilikili.web.entity.SearchSyncTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 搜索索引补偿任务表操作。
 * 同一视频只保留一条任务：重复入队时覆盖为最新操作并重置重试状态。
 */
@Mapper
public interface SearchSyncMapper {

    @Insert("INSERT INTO search_sync_task(video_id, operation, status, attempts, next_retry_at) " +
            "VALUES(#{videoId}, #{operation}, 'PENDING', 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE operation=VALUES(operation), status='PENDING', attempts=0, " +
            "next_retry_at=NOW(), last_error=NULL")
    int enqueue(@Param("videoId") long videoId, @Param("operation") String operation);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "video_id", javaType = long.class),
            @Arg(column = "operation", javaType = String.class),
            @Arg(column = "attempts", javaType = int.class)
    })
    @Select("SELECT id, video_id, operation, attempts FROM search_sync_task " +
            "WHERE status='PENDING' AND next_retry_at <= NOW() ORDER BY id LIMIT #{limit}")
    List<SearchSyncTask> pending(@Param("limit") int limit);

    @Update("UPDATE search_sync_task SET status='DONE', last_error=NULL WHERE id=#{id}")
    int markDone(long id);

    @Update("UPDATE search_sync_task SET attempts=attempts+1, next_retry_at=DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND), " +
            "last_error=#{error} WHERE id=#{id}")
    int markRetry(@Param("id") long id, @Param("delaySeconds") int delaySeconds, @Param("error") String error);

    @Update("UPDATE search_sync_task SET status='FAILED', attempts=attempts+1, last_error=#{error} WHERE id=#{id}")
    int markFailed(@Param("id") long id, @Param("error") String error);

    @Select("SELECT COUNT(*) FROM search_sync_task WHERE status=#{status}")
    long countByStatus(@Param("status") String status);

    @Delete("DELETE FROM search_sync_task WHERE status='DONE' AND updated_at < DATE_SUB(NOW(), INTERVAL 1 DAY)")
    int purgeDone();
}
