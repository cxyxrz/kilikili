package com.kilikili.web.mq;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface VideoPipelineMapper {
    @Insert("INSERT IGNORE INTO video_pipeline(video_id, stage, status, event_id) "
            + "VALUES(#{videoId}, #{stage}, 'PENDING', #{eventId})")
    int ensure(@Param("videoId") long videoId, @Param("stage") String stage, @Param("eventId") String eventId);

    @Update("UPDATE video_pipeline SET status=#{status}, attempts=attempts+1, error=#{error} "
            + "WHERE video_id=#{videoId} AND stage=#{stage}")
    int updateStatus(@Param("videoId") long videoId, @Param("stage") String stage,
                     @Param("status") String status, @Param("error") String error);

    @Select("SELECT status FROM video_pipeline WHERE video_id=#{videoId} AND stage=#{stage}")
    String findStatus(@Param("videoId") long videoId, @Param("stage") String stage);

    @Select("SELECT stage, status, COUNT(*) AS total, SUM(attempts) AS attempts FROM video_pipeline "
            + "GROUP BY stage, status ORDER BY stage, status")
    List<Map<String, Object>> countByStageStatus();

    /** 补偿扫描：长时间未完成的阶段（排除等待人工审核的 WAITING） */
    @Select("SELECT video_id, stage FROM video_pipeline WHERE status IN ('PENDING','FAILED') "
            + "AND updated_at < DATE_SUB(NOW(), INTERVAL #{delaySeconds} SECOND) "
            + "ORDER BY updated_at ASC LIMIT #{limit}")
    List<Map<String, Object>> findUnfinished(@Param("delaySeconds") int delaySeconds, @Param("limit") int limit);

    @Select("SELECT video_id, stage, status, attempts, LEFT(IFNULL(error,''),120) AS error, "
            + "created_at, updated_at FROM video_pipeline ORDER BY updated_at DESC LIMIT #{limit}")
    List<Map<String, Object>> recent(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM video_pipeline WHERE stage=#{stage} AND status='DONE'")
    long countDoneByStage(@Param("stage") String stage);

    @Select("SELECT LEFT(IFNULL(error,''),500) FROM video_pipeline WHERE video_id=#{videoId} AND stage=#{stage}")
    String findError(@Param("videoId") long videoId, @Param("stage") String stage);

    /** 等待审核阶段挂起的视频当前状态，用于审核结果出来后恢复流水线 */
    @Select("SELECT p.video_id, v.status FROM video_pipeline p JOIN video v ON v.id=p.video_id "
            + "WHERE p.stage='REVIEW' AND p.status='WAITING' LIMIT #{limit}")
    List<Map<String, Object>> findWaitingReview(@Param("limit") int limit);
}
