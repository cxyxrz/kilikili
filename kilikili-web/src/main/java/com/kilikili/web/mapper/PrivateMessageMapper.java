package com.kilikili.web.mapper;

import com.kilikili.web.entity.ConversationSource;
import com.kilikili.web.entity.PrivateMessageItem;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PrivateMessageMapper {
    @Insert("INSERT INTO private_message(from_id,to_id,content) VALUES(#{fromId},#{toId},#{content})")
    int insert(@Param("fromId") long fromId, @Param("toId") long toId, @Param("content") String content);

    @ConstructorArgs({
            @Arg(column = "peer_id", javaType = Long.class), @Arg(column = "peer_name", javaType = String.class),
            @Arg(column = "peer_avatar", javaType = String.class), @Arg(column = "last_content", javaType = String.class),
            @Arg(column = "last_at", javaType = java.time.LocalDateTime.class), @Arg(column = "unread", javaType = Long.class)
    })
    @Select("SELECT a.id AS peer_id, a.nickname AS peer_name, a.avatar AS peer_avatar, m.content AS last_content, m.created_at AS last_at, " +
            "(SELECT COUNT(*) FROM private_message x WHERE x.from_id = a.id AND x.to_id = #{userId} AND x.read_at IS NULL) AS unread " +
            "FROM private_message m " +
            "JOIN (SELECT MAX(id) AS id FROM private_message WHERE from_id = #{userId} OR to_id = #{userId} " +
            "GROUP BY IF(from_id = #{userId}, to_id, from_id)) latest ON latest.id = m.id " +
            "JOIN account a ON a.id = IF(m.from_id = #{userId}, m.to_id, m.from_id) " +
            "ORDER BY m.created_at DESC LIMIT #{limit}")
    List<ConversationSource> listConversations(@Param("userId") long userId, @Param("limit") int limit);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class), @Arg(column = "from_id", javaType = Long.class),
            @Arg(column = "to_id", javaType = Long.class), @Arg(column = "content", javaType = String.class),
            @Arg(column = "read_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("SELECT * FROM (SELECT id, from_id, to_id, content, read_at, created_at FROM private_message " +
            "WHERE (from_id = #{userId} AND to_id = #{peerId}) OR (to_id = #{userId} AND from_id = #{peerId}) " +
            "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}) latest ORDER BY latest.id ASC")
    List<PrivateMessageItem> listThread(@Param("userId") long userId, @Param("peerId") long peerId,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM private_message WHERE (from_id = #{userId} AND to_id = #{peerId}) OR (to_id = #{userId} AND from_id = #{peerId})")
    long countThread(@Param("userId") long userId, @Param("peerId") long peerId);

    @Update("UPDATE private_message SET read_at = CURRENT_TIMESTAMP WHERE from_id = #{peerId} AND to_id = #{userId} AND read_at IS NULL")
    int markRead(@Param("userId") long userId, @Param("peerId") long peerId);

    @Select("SELECT COUNT(*) FROM private_message WHERE to_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") long userId);
}
