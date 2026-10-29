package com.kilikili.web.mapper;

import com.kilikili.web.entity.LiveRoom;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LiveRoomMapper {
    String COLUMNS = "r.id, r.user_id, r.title, r.cover_object_key, r.stream_key, r.status, r.viewer_count, r.danmaku_count, "
            + "r.started_at, r.ended_at, r.created_at, a.nickname AS author_name, a.avatar AS author_avatar ";

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "cover_object_key", javaType = String.class),
            @Arg(column = "stream_key", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "viewer_count", javaType = Integer.class),
            @Arg(column = "danmaku_count", javaType = Integer.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "ended_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "author_name", javaType = String.class),
            @Arg(column = "author_avatar", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + "FROM live_room r JOIN account a ON a.id=r.user_id WHERE r.id=#{id}")
    LiveRoom findById(long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "cover_object_key", javaType = String.class),
            @Arg(column = "stream_key", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "viewer_count", javaType = Integer.class),
            @Arg(column = "danmaku_count", javaType = Integer.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "ended_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "author_name", javaType = String.class),
            @Arg(column = "author_avatar", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + "FROM live_room r JOIN account a ON a.id=r.user_id WHERE r.stream_key=#{streamKey}")
    LiveRoom findByStreamKey(String streamKey);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "cover_object_key", javaType = String.class),
            @Arg(column = "stream_key", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "viewer_count", javaType = Integer.class),
            @Arg(column = "danmaku_count", javaType = Integer.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "ended_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "author_name", javaType = String.class),
            @Arg(column = "author_avatar", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + "FROM live_room r JOIN account a ON a.id=r.user_id WHERE r.user_id=#{userId} ORDER BY r.id DESC LIMIT 1")
    LiveRoom findLatestByUser(long userId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "cover_object_key", javaType = String.class),
            @Arg(column = "stream_key", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "viewer_count", javaType = Integer.class),
            @Arg(column = "danmaku_count", javaType = Integer.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "ended_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "author_name", javaType = String.class),
            @Arg(column = "author_avatar", javaType = String.class)
    })
    @Select("SELECT " + COLUMNS + "FROM live_room r JOIN account a ON a.id=r.user_id "
            + "ORDER BY CASE r.status WHEN 'LIVING' THEN 0 WHEN 'READY' THEN 1 ELSE 2 END, r.created_at DESC LIMIT #{limit}")
    List<LiveRoom> list(int limit);

    @Insert("INSERT INTO live_room(user_id,title,cover_object_key,stream_key) VALUES(#{userId},#{title},#{coverObjectKey},#{streamKey})")
    int insert(@Param("userId") long userId, @Param("title") String title,
               @Param("coverObjectKey") String coverObjectKey, @Param("streamKey") String streamKey);

    @Update("UPDATE live_room SET title=#{title}, cover_object_key=#{coverObjectKey} WHERE id=#{id}")
    int updateRoomInfo(@Param("id") long id, @Param("title") String title, @Param("coverObjectKey") String coverObjectKey);

    @Update("UPDATE live_room SET status='LIVING', started_at=NOW(), ended_at=NULL WHERE id=#{id} AND status<>'LIVING'")
    int markLiving(long id);

    @Update("UPDATE live_room SET status='ENDED', ended_at=NOW(), viewer_count=0 WHERE id=#{id} AND status<>'ENDED'")
    int markEnded(long id);

    @Update("UPDATE live_room SET viewer_count=#{count} WHERE id=#{id}")
    int updateViewerCount(@Param("id") long id, @Param("count") int count);

    @Update("UPDATE live_room SET danmaku_count=danmaku_count+1 WHERE id=#{id}")
    int increaseDanmaku(long id);
}
