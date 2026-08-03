package com.kilikili.web.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FollowMapper {
    @Insert("INSERT IGNORE INTO user_follow(follower_id, following_id) VALUES(#{followerId}, #{followingId})")
    int insert(@Param("followerId") long followerId, @Param("followingId") long followingId);

    @Delete("DELETE FROM user_follow WHERE follower_id=#{followerId} AND following_id=#{followingId}")
    int delete(@Param("followerId") long followerId, @Param("followingId") long followingId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id=#{followerId} AND following_id=#{followingId}")
    int hasFollowed(@Param("followerId") long followerId, @Param("followingId") long followingId);
}
