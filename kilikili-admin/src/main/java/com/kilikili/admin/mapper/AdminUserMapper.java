package com.kilikili.admin.mapper;

import com.kilikili.admin.entity.AdminUserSource;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminUserMapper {
    @Select("""
            <script>
            SELECT id, username, email, nickname, avatar, gender, status, created_at, last_login_at
            FROM account
            <where>
              role = 'USER'
              <if test='keyword != null and keyword != ""'>
                AND (nickname LIKE CONCAT('%', #{keyword}, '%')
                     OR username LIKE CONCAT('%', #{keyword}, '%')
                     OR email LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test='status != null and status != ""'>AND status = #{status}</if>
            </where>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "email", javaType = String.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class),
            @Arg(column = "gender", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "last_login_at", javaType = java.time.LocalDateTime.class)
    })
    List<AdminUserSource> list(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("offset") long offset,
                               @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM account
            <where>
              role = 'USER'
              <if test='keyword != null and keyword != ""'>
                AND (nickname LIKE CONCAT('%', #{keyword}, '%')
                     OR username LIKE CONCAT('%', #{keyword}, '%')
                     OR email LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test='status != null and status != ""'>AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("status") String status);

    @Update("UPDATE account SET status=#{status} WHERE id=#{id} AND role='USER'")
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
