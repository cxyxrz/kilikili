package com.kilikili.web.mapper;

import com.kilikili.web.entity.Category;
import com.kilikili.web.entity.RecommendSource;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RecommendationMapper {
    @Select("SELECT id, name FROM category WHERE status='ACTIVE' ORDER BY sort_order, id")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="name", javaType=String.class)})
    List<Category> listCategories();

    @Select("<script>SELECT v.id, v.title, a.nickname AS author_name, v.cover_object_key, v.duration_seconds, v.view_count, v.like_count, v.comment_count, v.category_id FROM video v JOIN account a ON a.id=v.author_id WHERE v.status='PUBLISHED' <if test='categoryId != null and categoryId &gt; 0'>AND v.category_id=#{categoryId}</if> ORDER BY ((v.view_count + v.like_count * 3 + v.comment_count * 4) / (1 + TIMESTAMPDIFF(HOUR, v.created_at, NOW()) / 48.0)) DESC, v.created_at DESC LIMIT #{limit} OFFSET #{offset}</script>")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="title", javaType=String.class), @Arg(column="author_name", javaType=String.class), @Arg(column="cover_object_key", javaType=String.class), @Arg(column="duration_seconds", javaType=Integer.class), @Arg(column="view_count", javaType=Long.class), @Arg(column="like_count", javaType=Long.class), @Arg(column="comment_count", javaType=Long.class), @Arg(column="category_id", javaType=Long.class)})
    List<RecommendSource> listRecommend(@Param("categoryId") Long categoryId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM video WHERE status='PUBLISHED' <if test='categoryId != null and categoryId &gt; 0'>AND category_id=#{categoryId}</if></script>")
    long countRecommend(@Param("categoryId") Long categoryId);
}
