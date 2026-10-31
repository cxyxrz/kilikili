package com.kilikili.web.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频删除时的关联数据清理。
 * 表间没有外键约束，需要按 video_id 逐表删除，避免留下孤儿数据。
 */
@Mapper
public interface VideoCleanupMapper {
    @Delete("DELETE FROM video_comment WHERE video_id=#{videoId}")
    int deleteComments(long videoId);

    @Delete("DELETE FROM video_danmaku WHERE video_id=#{videoId}")
    int deleteDanmakus(long videoId);

    @Delete("DELETE FROM video_like WHERE video_id=#{videoId}")
    int deleteLikes(long videoId);

    @Delete("DELETE FROM video_favorite WHERE video_id=#{videoId}")
    int deleteFavorites(long videoId);

    @Delete("DELETE FROM video_view_history WHERE video_id=#{videoId}")
    int deleteViewHistory(long videoId);

    @Delete("DELETE FROM video_play_progress WHERE video_id=#{videoId}")
    int deletePlayProgress(long videoId);

    @Delete("DELETE FROM video_danmaku_analysis WHERE video_id=#{videoId}")
    int deleteDanmakuAnalysis(long videoId);

    @Delete("DELETE FROM video_danmaku_mask WHERE video_id=#{videoId}")
    int deleteDanmakuMasks(long videoId);

    @Delete("DELETE FROM video_audit WHERE video_id=#{videoId}")
    int deleteAudits(long videoId);

    @Delete("DELETE FROM transcode_task_attempt WHERE video_id=#{videoId}")
    int deleteTranscodeAttempts(long videoId);

    @Delete("DELETE FROM transcode_task_progress WHERE video_id=#{videoId}")
    int deleteTranscodeProgress(long videoId);

    @Delete("DELETE FROM transcode_task WHERE video_id=#{videoId}")
    int deleteTranscodeTasks(long videoId);

    @Delete("DELETE FROM video_pipeline WHERE video_id=#{videoId}")
    int deletePipeline(long videoId);

    @Delete("DELETE FROM mq_consume_log WHERE video_id=#{videoId}")
    int deleteConsumeLogs(long videoId);

    @Delete("DELETE FROM mq_dead_letter WHERE video_id=#{videoId}")
    int deleteDeadLetters(long videoId);

    @Delete("DELETE FROM user_notification WHERE video_id=#{videoId}")
    int deleteNotifications(long videoId);

    @Delete("DELETE FROM video WHERE id=#{videoId}")
    int deleteVideo(long videoId);
}
