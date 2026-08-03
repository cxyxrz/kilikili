package com.kilikili.web.mapper;

import com.kilikili.web.entity.FollowerItem;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper {
    @Insert("INSERT IGNORE INTO user_follow(follower_id, following_id) VALUES(#{followerId}, #{followingId})")
    int insert(@Param("followerId") long followerId, @Param("followingId") long followingId);

    @Delete("DELETE FROM user_follow WHERE follower_id=#{followerId} AND following_id=#{followingId}")
    int delete(@Param("followerId") long followerId, @Param("followingId") long followingId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id=#{followerId} AND following_id=#{followingId}")
    int hasFollowed(@Param("followerId") long followerId, @Param("followingId") long followingId);

    @ConstructorArgs({
            @Arg(column="id", javaType=Long.class), @Arg(column="nickname", javaType=String.class),
            @Arg(column="avatar", javaType=String.class), @Arg(column="followed_at", javaType=java.time.LocalDateTime.class)
    })
    @Select("SELECT a.id,a.nickname,a.avatar,f.created_at AS followed_at FROM user_follow f JOIN account a ON a.id=f.follower_id WHERE f.following_id=#{userId} ORDER BY f.created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<FollowerItem> listFollowers(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM user_follow WHERE following_id=#{userId}")
    long countFollowers(@Param("userId") long userId);
}
