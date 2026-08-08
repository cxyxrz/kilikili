package com.kilikili.admin.mapper;

import com.kilikili.admin.entity.AdminCategoryItem;
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
public interface AdminCategoryMapper {
    @Select("""
            SELECT c.id,
                   c.name,
                   c.code,
                   c.parent_id,
                   c.sort_order,
                   (SELECT COUNT(*) FROM category child WHERE child.parent_id = c.id) AS child_count,
                   (SELECT COUNT(*) FROM video v WHERE v.category_id = c.id) AS video_count
            FROM category c
            ORDER BY CASE WHEN c.parent_id IS NULL THEN c.sort_order ELSE 100000 + c.sort_order END,
                     c.id
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "code", javaType = String.class),
            @Arg(column = "parent_id", javaType = Long.class),
            @Arg(column = "sort_order", javaType = Integer.class),
            @Arg(column = "child_count", javaType = Long.class),
            @Arg(column = "video_count", javaType = Long.class)
    })
    List<AdminCategoryItem> list();

    @Select("""
            SELECT c.id,
                   c.name,
                   c.code,
                   c.parent_id,
                   c.sort_order,
                   (SELECT COUNT(*) FROM category child WHERE child.parent_id = c.id) AS child_count,
                   (SELECT COUNT(*) FROM video v WHERE v.category_id = c.id) AS video_count
            FROM category c
            WHERE c.id = #{id}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "code", javaType = String.class),
            @Arg(column = "parent_id", javaType = Long.class),
            @Arg(column = "sort_order", javaType = Integer.class),
            @Arg(column = "child_count", javaType = Long.class),
            @Arg(column = "video_count", javaType = Long.class)
    })
    AdminCategoryItem findById(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM category WHERE name = #{name} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM category WHERE code = #{code} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Insert("INSERT INTO category(name, code, parent_id, sort_order, status) VALUES(#{name}, #{code}, #{parentId}, #{sortOrder}, 'ACTIVE')")
    int insert(@Param("name") String name,
               @Param("code") String code,
               @Param("parentId") Long parentId,
               @Param("sortOrder") int sortOrder);

    @Select("SELECT id FROM category WHERE code = #{code}")
    Long findIdByCode(@Param("code") String code);

    @Update("UPDATE category SET name=#{name}, code=#{code}, parent_id=#{parentId}, sort_order=#{sortOrder} WHERE id=#{id}")
    int update(@Param("id") long id,
               @Param("name") String name,
               @Param("code") String code,
               @Param("parentId") Long parentId,
               @Param("sortOrder") int sortOrder);

    @Select("SELECT COUNT(*) FROM category WHERE parent_id = #{id}")
    int countChildren(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM video WHERE category_id = #{id}")
    int countVideos(@Param("id") long id);

    @Delete("DELETE FROM category WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
