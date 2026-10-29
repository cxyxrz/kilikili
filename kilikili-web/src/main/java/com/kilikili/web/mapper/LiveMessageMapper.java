package com.kilikili.web.mapper;

import com.kilikili.web.entity.LiveMessage;
import com.kilikili.web.entity.LiveMessageInsert;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LiveMessageMapper {
    @Insert("INSERT INTO live_message(room_id,user_id,type,content) VALUES(#{roomId},#{userId},#{type},#{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LiveMessageInsert message);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "room_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "type", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class)
    })
    @Select("SELECT m.id,m.room_id,m.user_id,m.type,m.content,m.created_at,a.nickname,a.avatar "
            + "FROM live_message m LEFT JOIN account a ON a.id=m.user_id WHERE m.id=#{id}")
    LiveMessage findById(long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "room_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "type", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class)
    })
    @Select("SELECT m.id,m.room_id,m.user_id,m.type,m.content,m.created_at,a.nickname,a.avatar "
            + "FROM live_message m LEFT JOIN account a ON a.id=m.user_id WHERE m.room_id=#{roomId} "
            + "ORDER BY m.id DESC LIMIT #{limit}")
    List<LiveMessage> listRecent(@Param("roomId") long roomId, @Param("limit") int limit);
}
