package com.kilikili.web.mapper;

import com.kilikili.web.entity.TranscodeTaskProgress;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TranscodeProgressMapper {
    String COLUMNS = "task_id, video_id, percent, stage, fps, speed, eta_seconds, message, updated_at ";

    @ConstructorArgs({
            @Arg(column = "task_id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "percent", javaType = Integer.class),
            @Arg(column = "stage", javaType = String.class),
            @Arg(column = "fps", javaType = BigDecimal.class),
            @Arg(column = "speed", javaType = BigDecimal.class),
            @Arg(column = "eta_seconds", javaType = Integer.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    @Select("SELECT " + COLUMNS + "FROM transcode_task_progress WHERE task_id=#{taskId}")
    TranscodeTaskProgress findByTaskId(long taskId);

    @ConstructorArgs({
            @Arg(column = "task_id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "percent", javaType = Integer.class),
            @Arg(column = "stage", javaType = String.class),
            @Arg(column = "fps", javaType = BigDecimal.class),
            @Arg(column = "speed", javaType = BigDecimal.class),
            @Arg(column = "eta_seconds", javaType = Integer.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    @Select("SELECT " + COLUMNS + "FROM transcode_task_progress WHERE video_id=#{videoId} ORDER BY updated_at DESC LIMIT 1")
    TranscodeTaskProgress findLatestByVideoId(long videoId);

    @Insert("""
            INSERT INTO transcode_task_progress(task_id, video_id, percent, stage, fps, speed, eta_seconds, message)
            VALUES(#{taskId}, #{videoId}, #{percent}, #{stage}, #{fps}, #{speed}, #{etaSeconds}, #{message})
            ON DUPLICATE KEY UPDATE percent=VALUES(percent), stage=VALUES(stage), fps=VALUES(fps),
              speed=VALUES(speed), eta_seconds=VALUES(eta_seconds), message=VALUES(message), updated_at=NOW()
            """)
    int upsert(@Param("taskId") long taskId, @Param("videoId") long videoId, @Param("percent") int percent,
               @Param("stage") String stage, @Param("fps") BigDecimal fps, @Param("speed") BigDecimal speed,
               @Param("etaSeconds") Integer etaSeconds, @Param("message") String message);

    @Select("SELECT COUNT(*) FROM transcode_task WHERE status IN ('PENDING','RUNNING')")
    int countUnfinished();

    @Select("SELECT status, COUNT(*) AS total FROM transcode_task GROUP BY status")
    List<Map<String, Object>> countByStatus();
}
