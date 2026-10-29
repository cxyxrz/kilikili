package com.kilikili.web.mapper;

import com.kilikili.web.entity.DanmakuHeatBucket;
import com.kilikili.web.entity.DanmakuMaskAnalysis;
import com.kilikili.web.entity.DanmakuMaskSegment;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 弹幕增强能力的数据访问：防挡脸遮罩、热度图分桶 */
@Mapper
public interface DanmakuInsightMapper {

    @ConstructorArgs({
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "frame_count", javaType = Integer.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "updated_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("SELECT video_id,status,frame_count,message,updated_at FROM video_danmaku_analysis WHERE video_id=#{videoId}")
    DanmakuMaskAnalysis findAnalysis(@Param("videoId") long videoId);

    @Insert("INSERT INTO video_danmaku_analysis(video_id,status,frame_count,message) " +
            "VALUES(#{videoId},#{status},#{frameCount},#{message}) " +
            "ON DUPLICATE KEY UPDATE status=VALUES(status),frame_count=VALUES(frame_count),message=VALUES(message),updated_at=CURRENT_TIMESTAMP")
    int upsertAnalysis(@Param("videoId") long videoId, @Param("status") String status,
                       @Param("frameCount") int frameCount, @Param("message") String message);

    @Delete("DELETE FROM video_danmaku_mask WHERE video_id=#{videoId}")
    int deleteSegments(@Param("videoId") long videoId);

    @Insert("INSERT INTO video_danmaku_mask(video_id,start_ms,end_ms,mask_top,mask_bottom,face_ratio) " +
            "VALUES(#{videoId},#{startMs},#{endMs},#{top},#{bottom},#{faceRatio})")
    int insertSegment(@Param("videoId") long videoId, @Param("startMs") int startMs, @Param("endMs") int endMs,
                      @Param("top") double top, @Param("bottom") double bottom, @Param("faceRatio") double faceRatio);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "start_ms", javaType = Integer.class),
            @Arg(column = "end_ms", javaType = Integer.class),
            @Arg(column = "mask_top", javaType = Double.class),
            @Arg(column = "mask_bottom", javaType = Double.class),
            @Arg(column = "face_ratio", javaType = Double.class)
    })
    @Select("SELECT id,video_id,start_ms,end_ms,mask_top,mask_bottom,face_ratio FROM video_danmaku_mask " +
            "WHERE video_id=#{videoId} ORDER BY start_ms ASC")
    List<DanmakuMaskSegment> listSegments(@Param("videoId") long videoId);

    @ConstructorArgs({
            @Arg(column = "bucket", javaType = Long.class),
            @Arg(column = "count", javaType = Long.class)
    })
    @Select("SELECT FLOOR(time_ms / #{bucketMs}) AS bucket, COUNT(*) AS count FROM video_danmaku " +
            "WHERE video_id=#{videoId} GROUP BY bucket ORDER BY bucket ASC")
    List<DanmakuHeatBucket> heatBuckets(@Param("videoId") long videoId, @Param("bucketMs") long bucketMs);
}
