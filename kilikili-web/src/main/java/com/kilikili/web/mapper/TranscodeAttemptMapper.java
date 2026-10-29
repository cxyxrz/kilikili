package com.kilikili.web.mapper;

import com.kilikili.web.entity.TranscodeTaskAttempt;
import com.kilikili.web.entity.TranscodeTaskAttemptInsert;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface TranscodeAttemptMapper {
    @Insert("INSERT INTO transcode_task_attempt(task_id, video_id, attempt_no, status) "
            + "VALUES(#{taskId}, #{videoId}, #{attemptNo}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TranscodeTaskAttemptInsert insert);

    @Update("UPDATE transcode_task_attempt SET status=#{status}, error_message=#{errorMessage}, "
            + "finished_at=NOW(), duration_ms=TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) DIV 1000 "
            + "WHERE id=#{id}")
    int finish(@Param("id") long id, @Param("status") String status, @Param("errorMessage") String errorMessage);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "task_id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "attempt_no", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "error_message", javaType = String.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "finished_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "duration_ms", javaType = Long.class)
    })
    @Select("SELECT id, task_id, video_id, attempt_no, status, error_message, started_at, finished_at, duration_ms "
            + "FROM transcode_task_attempt WHERE task_id=#{taskId} ORDER BY id ASC")
    List<TranscodeTaskAttempt> listByTaskId(long taskId);

    @Update("UPDATE transcode_task_attempt SET status='FAILED', error_message='服务重启，尝试中断', "
            + "finished_at=NOW(), duration_ms=TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) DIV 1000 "
            + "WHERE status='RUNNING'")
    int failStaleRunning();

    /** 终态任务的耗时统计（秒），用于观测单次转码平均/分位耗时 */
    @Select("""
            SELECT COUNT(*) AS total,
                   SUM(status='COMPLETED') AS completed,
                   SUM(status='FAILED') AS failed,
                   AVG(duration_ms) AS avgMs,
                   MAX(duration_ms) AS maxMs,
                   SUM(attempt_no > 1) AS retried
            FROM transcode_task_attempt
            WHERE status IN ('COMPLETED','FAILED')
            """)
    Map<String, Object> summary();

    /** 成功任务的 P95 耗时：窗口函数取第 95% 位置（MySQL 8） */
    @Select("""
            SELECT duration_ms FROM (
              SELECT duration_ms,
                     ROW_NUMBER() OVER (ORDER BY duration_ms) AS rn,
                     COUNT(*) OVER () AS cnt
              FROM transcode_task_attempt
              WHERE status='COMPLETED' AND duration_ms IS NOT NULL
            ) ranked
            WHERE rn = GREATEST(1, CEIL(cnt * 0.95))
            """)
    Long p95DurationMs();
}
