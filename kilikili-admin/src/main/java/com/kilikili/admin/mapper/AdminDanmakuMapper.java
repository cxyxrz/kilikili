package com.kilikili.admin.mapper;

import com.kilikili.admin.entity.AdminDanmakuItem;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminDanmakuMapper {

    @Select("""
            SELECT d.id,
                   d.content,
                   a.nickname,
                   v.title AS video_title,
                   d.time_ms,
                   d.created_at
            FROM video_danmaku d
            JOIN account a ON a.id = d.user_id
            JOIN video v ON v.id = d.video_id
            ORDER BY d.created_at DESC, d.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "video_title", javaType = String.class),
            @Arg(column = "time_ms", javaType = Integer.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AdminDanmakuItem> list(@Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM video_danmaku")
    long count();

    @Delete("DELETE FROM video_danmaku WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
