package com.kilikili.admin.mapper;

import com.kilikili.admin.entity.AdminCommentItem;
import com.kilikili.admin.entity.AdminCommentTarget;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminCommentMapper {
    @Select("""
            <script>
            SELECT c.id,
                   c.parent_id,
                   c.content,
                   a.username,
                   a.nickname,
                   v.title AS video_title,
                   (SELECT COUNT(*) FROM video_comment reply WHERE reply.parent_id = c.id) AS reply_count,
                   c.created_at
            FROM video_comment c
            JOIN account a ON a.id = c.user_id
            JOIN video v ON v.id = c.video_id
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (c.content LIKE CONCAT('%', #{keyword}, '%')
                     OR a.username LIKE CONCAT('%', #{keyword}, '%')
                     OR a.nickname LIKE CONCAT('%', #{keyword}, '%')
                     OR v.title LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test='type == "ROOT"'>AND c.parent_id IS NULL</if>
              <if test='type == "REPLY"'>AND c.parent_id IS NOT NULL</if>
            </where>
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "parent_id", javaType = Long.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "video_title", javaType = String.class),
            @Arg(column = "reply_count", javaType = Long.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AdminCommentItem> list(@Param("keyword") String keyword,
                                @Param("type") String type,
                                @Param("offset") long offset,
                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM video_comment c
            JOIN account a ON a.id = c.user_id
            JOIN video v ON v.id = c.video_id
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (c.content LIKE CONCAT('%', #{keyword}, '%')
                     OR a.username LIKE CONCAT('%', #{keyword}, '%')
                     OR a.nickname LIKE CONCAT('%', #{keyword}, '%')
                     OR v.title LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test='type == "ROOT"'>AND c.parent_id IS NULL</if>
              <if test='type == "REPLY"'>AND c.parent_id IS NOT NULL</if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("type") String type);

    @Select("SELECT id, video_id, parent_id FROM video_comment WHERE id = #{id}")
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "parent_id", javaType = Long.class)
    })
    AdminCommentTarget findTarget(@Param("id") long id);

    @Delete("DELETE FROM video_comment WHERE id = #{id} OR parent_id = #{id}")
    int deleteThread(@Param("id") long id);

    @Delete("DELETE FROM video_comment WHERE id = #{id}")
    int deleteOne(@Param("id") long id);

    @Update("UPDATE video SET comment_count = GREATEST(comment_count - #{count}, 0) WHERE id = #{videoId}")
    int decreaseVideoCommentCount(@Param("videoId") long videoId, @Param("count") int count);
}
