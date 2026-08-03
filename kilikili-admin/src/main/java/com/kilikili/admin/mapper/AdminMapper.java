package com.kilikili.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import java.util.List;
import java.util.Map;
import com.kilikili.admin.entity.AdminAccount;

@Mapper
public interface AdminMapper {
    @Select("SELECT id,username,password_hash,nickname,status,role FROM account WHERE username=#{username}")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="username", javaType=String.class), @Arg(column="password_hash", javaType=String.class), @Arg(column="nickname", javaType=String.class), @Arg(column="status", javaType=String.class), @Arg(column="role", javaType=String.class)})
    AdminAccount findAccountByUsername(@Param("username") String username);

    @Select("SELECT id,username,password_hash,nickname,status,role FROM account WHERE id=#{id}")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="username", javaType=String.class), @Arg(column="password_hash", javaType=String.class), @Arg(column="nickname", javaType=String.class), @Arg(column="status", javaType=String.class), @Arg(column="role", javaType=String.class)})
    AdminAccount findAccountById(@Param("id") long id);

    @Insert("INSERT INTO account(username,password_hash,nickname,status,role) VALUES(#{username},#{passwordHash},#{nickname},'ACTIVE','ADMIN')")
    int insertBootstrapAdmin(@Param("username") String username, @Param("passwordHash") String passwordHash, @Param("nickname") String nickname);
    @Select("SELECT (SELECT COUNT(*) FROM account) AS users, (SELECT COUNT(*) FROM video) AS videos, (SELECT COUNT(*) FROM video WHERE status='PENDING_REVIEW') AS pendingVideos, (SELECT COUNT(*) FROM video_comment) AS comments")
    Map<String, Object> dashboard();

    @Select("<script>SELECT id,username,nickname,status,created_at AS createdAt FROM account <where><if test='keyword != null and keyword != \"\"'>AND (username LIKE CONCAT('%',#{keyword},'%') OR nickname LIKE CONCAT('%',#{keyword},'%'))</if></where> ORDER BY created_at DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> users(@Param("keyword") String keyword, @Param("limit") int limit);

    @Update("UPDATE account SET status=#{status} WHERE id=#{id}")
    int updateUserStatus(@Param("id") long id, @Param("status") String status);

    @Select("<script>SELECT v.id,v.title,v.description,v.status,v.view_count AS viewCount,v.like_count AS likeCount,v.comment_count AS commentCount,v.created_at AS createdAt,a.username,a.nickname FROM video v JOIN account a ON a.id=v.author_id <where><if test='status != null and status != \"\"'>AND v.status=#{status}</if><if test='keyword != null and keyword != \"\"'>AND (v.title LIKE CONCAT('%',#{keyword},'%') OR a.username LIKE CONCAT('%',#{keyword},'%'))</if></where> ORDER BY v.created_at DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> videos(@Param("status") String status, @Param("keyword") String keyword, @Param("limit") int limit);

    @Update("UPDATE video SET status=#{status} WHERE id=#{id}")
    int updateVideoStatus(@Param("id") long id, @Param("status") String status);
}
