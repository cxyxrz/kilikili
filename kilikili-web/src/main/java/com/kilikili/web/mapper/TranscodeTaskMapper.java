package com.kilikili.web.mapper;

import com.kilikili.web.entity.TranscodeTask;
import com.kilikili.web.entity.TranscodeTaskInsert;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TranscodeTaskMapper {
    String TASK_COLUMNS = "id, video_id, status, attempt_count, max_attempts, error_message, hls_playlist_key, renditions, started_at, finished_at, created_at ";

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "attempt_count", javaType = Integer.class),
            @Arg(column = "max_attempts", javaType = Integer.class),
            @Arg(column = "error_message", javaType = String.class),
            @Arg(column = "hls_playlist_key", javaType = String.class),
            @Arg(column = "renditions", javaType = String.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "finished_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("SELECT " + TASK_COLUMNS + "FROM transcode_task WHERE id=#{id}")
    TranscodeTask findById(long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "attempt_count", javaType = Integer.class),
            @Arg(column = "max_attempts", javaType = Integer.class),
            @Arg(column = "error_message", javaType = String.class),
            @Arg(column = "hls_playlist_key", javaType = String.class),
            @Arg(column = "renditions", javaType = String.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "finished_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("SELECT " + TASK_COLUMNS + "FROM transcode_task WHERE video_id=#{videoId} ORDER BY id DESC LIMIT 1")
    TranscodeTask findLatestByVideoId(long videoId);

    @Insert("INSERT INTO transcode_task(video_id, max_attempts) VALUES(#{videoId}, #{maxAttempts})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TranscodeTaskInsert task);

    @Select("SELECT id FROM transcode_task WHERE status='PENDING' ORDER BY id ASC LIMIT #{limit}")
    List<Long> findPendingIds(int limit);

    @Update("UPDATE transcode_task SET status='RUNNING', attempt_count=attempt_count+1, started_at=COALESCE(started_at, CURRENT_TIMESTAMP) WHERE id=#{id} AND status='PENDING'")
    int claim(long id);

    @Update("UPDATE transcode_task SET status='PENDING', error_message='服务重启，任务重新排队' WHERE status='RUNNING'")
    int resetStaleRunning();

    @Update("UPDATE transcode_task SET status='COMPLETED', hls_playlist_key=#{playlistKey}, renditions=#{renditions}, error_message=NULL, finished_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int complete(@Param("id") long id, @Param("playlistKey") String playlistKey, @Param("renditions") String renditions);

    @Update("UPDATE transcode_task SET status='PENDING', error_message=#{message} WHERE id=#{id}")
    int requeue(@Param("id") long id, @Param("message") String message);

    @Update("UPDATE transcode_task SET status='FAILED', error_message=#{message}, finished_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int fail(@Param("id") long id, @Param("message") String message);
}
