package com.kilikili.web.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CreatorDashboardMapper {
    @Select("SELECT COUNT(*) AS videoCount, COALESCE(SUM(view_count),0) AS viewCount, COALESCE(SUM(like_count),0) AS likeCount, COALESCE(SUM(comment_count),0) AS commentCount FROM video WHERE author_id=#{authorId} AND status='PUBLISHED'")
    Map<String, Object> totals(@Param("authorId") long authorId);

    @Select("SELECT COUNT(*) FROM video_favorite f JOIN video v ON v.id=f.video_id WHERE v.author_id=#{authorId} AND v.status='PUBLISHED'")
    long favoriteCount(@Param("authorId") long authorId);

    @Select("SELECT " +
            "(SELECT COUNT(*) FROM video_view_history h JOIN video v ON v.id=h.video_id WHERE v.author_id=#{authorId} AND h.watched_at>=#{start}) AS views, " +
            "(SELECT COUNT(*) FROM video_like l JOIN video v ON v.id=l.video_id WHERE v.author_id=#{authorId} AND l.created_at>=#{start}) AS likes, " +
            "(SELECT COUNT(*) FROM video_comment c JOIN video v ON v.id=c.video_id WHERE v.author_id=#{authorId} AND c.created_at>=#{start}) AS comments, " +
            "(SELECT COUNT(*) FROM video_favorite f JOIN video v ON v.id=f.video_id WHERE v.author_id=#{authorId} AND f.created_at>=#{start}) AS favorites, " +
            "(SELECT COUNT(*) FROM user_follow f WHERE f.following_id=#{authorId} AND f.created_at>=#{start}) AS followers")
    Map<String, Object> periodTotals(@Param("authorId") long authorId, @Param("start") LocalDateTime start);

    @Select("SELECT DATE_FORMAT(h.watched_at,'%Y-%m-%d') AS day, COUNT(*) AS cnt FROM video_view_history h JOIN video v ON v.id=h.video_id WHERE v.author_id=#{authorId} AND h.watched_at>=#{start} GROUP BY day")
    List<Map<String, Object>> dailyViews(@Param("authorId") long authorId, @Param("start") LocalDateTime start);

    @Select("SELECT DATE_FORMAT(l.created_at,'%Y-%m-%d') AS day, COUNT(*) AS cnt FROM video_like l JOIN video v ON v.id=l.video_id WHERE v.author_id=#{authorId} AND l.created_at>=#{start} GROUP BY day")
    List<Map<String, Object>> dailyLikes(@Param("authorId") long authorId, @Param("start") LocalDateTime start);

    @Select("SELECT DATE_FORMAT(c.created_at,'%Y-%m-%d') AS day, COUNT(*) AS cnt FROM video_comment c JOIN video v ON v.id=c.video_id WHERE v.author_id=#{authorId} AND c.created_at>=#{start} GROUP BY day")
    List<Map<String, Object>> dailyComments(@Param("authorId") long authorId, @Param("start") LocalDateTime start);

    @Select("SELECT DATE_FORMAT(f.created_at,'%Y-%m-%d') AS day, COUNT(*) AS cnt FROM user_follow f WHERE f.following_id=#{authorId} AND f.created_at>=#{start} GROUP BY day")
    List<Map<String, Object>> dailyFollowers(@Param("authorId") long authorId, @Param("start") LocalDateTime start);

    @Select("SELECT a.gender AS name, COUNT(*) AS cnt FROM user_follow f JOIN account a ON a.id=f.follower_id WHERE f.following_id=#{authorId} GROUP BY a.gender")
    List<Map<String, Object>> followerGenders(@Param("authorId") long authorId);

    @Select("SELECT CASE WHEN a.birthday IS NULL THEN 'UNKNOWN' WHEN TIMESTAMPDIFF(YEAR,a.birthday,CURDATE())<18 THEN 'UNDER18' " +
            "WHEN TIMESTAMPDIFF(YEAR,a.birthday,CURDATE())<25 THEN '18-24' WHEN TIMESTAMPDIFF(YEAR,a.birthday,CURDATE())<35 THEN '25-34' " +
            "WHEN TIMESTAMPDIFF(YEAR,a.birthday,CURDATE())<45 THEN '35-44' ELSE '45+' END AS bucket, COUNT(*) AS cnt " +
            "FROM user_follow f JOIN account a ON a.id=f.follower_id WHERE f.following_id=#{authorId} GROUP BY bucket")
    List<Map<String, Object>> followerAges(@Param("authorId") long authorId);

    @Select("SELECT HOUR(h.watched_at) AS hr, COUNT(*) AS cnt FROM video_view_history h JOIN video v ON v.id=h.video_id WHERE v.author_id=#{authorId} GROUP BY hr")
    List<Map<String, Object>> viewHours(@Param("authorId") long authorId);

    @Select("SELECT v.id,v.title,v.cover_object_key,v.view_count,v.like_count,v.comment_count,v.created_at," +
            "(SELECT COUNT(*) FROM video_favorite f WHERE f.video_id=v.id) AS favorite_count FROM video v " +
            "WHERE v.author_id=#{authorId} AND v.status='PUBLISHED' ORDER BY v.view_count DESC LIMIT 20")
    List<Map<String, Object>> videoComparison(@Param("authorId") long authorId);
}
