package com.kilikili.web.progress;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlayProgressMapper {

    @Select("""
            SELECT progress_seconds AS progressSeconds,duration_seconds AS durationSeconds
            FROM video_play_progress WHERE user_id=#{userId} AND video_id=#{videoId}
            """)
    PlayProgressSnapshot find(@Param("userId") long userId, @Param("videoId") long videoId);

    @Insert("""
            INSERT INTO video_play_progress(user_id,video_id,progress_seconds,duration_seconds,updated_at)
            VALUES(#{userId},#{videoId},#{progress},#{duration},CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE progress_seconds=#{progress},duration_seconds=#{duration},updated_at=CURRENT_TIMESTAMP
            """)
    int upsert(@Param("userId") long userId,
               @Param("videoId") long videoId,
               @Param("progress") int progress,
               @Param("duration") int duration);
}
