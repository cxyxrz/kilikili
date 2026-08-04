package com.kilikili.web.mapper;

import com.kilikili.web.entity.NotificationSource;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {
    @Insert("INSERT INTO user_notification(recipient_id,actor_id,type,content,video_id) VALUES(#{recipientId},#{actorId},#{type},#{content},#{videoId})")
    int insert(@Param("recipientId") long recipientId, @Param("actorId") long actorId, @Param("type") String type,
               @Param("content") String content, @Param("videoId") Long videoId);

    @ConstructorArgs({
            @Arg(column="id", javaType=Long.class), @Arg(column="type", javaType=String.class),
            @Arg(column="content", javaType=String.class), @Arg(column="video_id", javaType=Long.class),
            @Arg(column="read_at", javaType=java.time.LocalDateTime.class), @Arg(column="created_at", javaType=java.time.LocalDateTime.class),
            @Arg(column="actor_name", javaType=String.class), @Arg(column="actor_avatar", javaType=String.class)
    })
    @Select("<script>SELECT n.id,n.type,n.content,n.video_id,n.read_at,n.created_at,a.nickname AS actor_name,a.avatar AS actor_avatar FROM user_notification n JOIN account a ON a.id=n.actor_id <where>n.recipient_id=#{recipientId} <if test='type != null and type != \"\"'>AND n.type=#{type}</if></where> ORDER BY n.created_at DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<NotificationSource> list(@Param("recipientId") long recipientId, @Param("type") String type, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM user_notification <where>recipient_id=#{recipientId} <if test='type != null and type != \"\"'>AND type=#{type}</if></where></script>")
    long count(@Param("recipientId") long recipientId, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM user_notification WHERE recipient_id=#{recipientId} AND read_at IS NULL")
    long countUnread(@Param("recipientId") long recipientId);

    @Update("UPDATE user_notification SET read_at=CURRENT_TIMESTAMP WHERE recipient_id=#{recipientId} AND read_at IS NULL")
    int markAllRead(@Param("recipientId") long recipientId);

    @Delete("DELETE FROM user_notification WHERE id=#{id} AND recipient_id=#{recipientId}")
    int delete(@Param("recipientId") long recipientId, @Param("id") long id);
}
