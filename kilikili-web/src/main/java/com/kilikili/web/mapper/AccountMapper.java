package com.kilikili.web.mapper;

import com.kilikili.web.entity.Account;
import com.kilikili.web.entity.AccountProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    @Select("SELECT id, username, password_hash, nickname, avatar, created_at, status FROM account WHERE username = #{username}")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="username", javaType=String.class), @Arg(column="password_hash", javaType=String.class), @Arg(column="nickname", javaType=String.class), @Arg(column="avatar", javaType=String.class), @Arg(column="created_at", javaType=java.time.LocalDateTime.class), @Arg(column="status", javaType=String.class)})
    Account findByUsername(String username);
    @Select("SELECT id, username, password_hash, nickname, avatar, created_at, status FROM account WHERE id = #{id}")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="username", javaType=String.class), @Arg(column="password_hash", javaType=String.class), @Arg(column="nickname", javaType=String.class), @Arg(column="avatar", javaType=String.class), @Arg(column="created_at", javaType=java.time.LocalDateTime.class), @Arg(column="status", javaType=String.class)})
    Account findById(long id);
    @Insert("INSERT INTO account(username, password_hash, nickname, status) VALUES(#{username}, #{passwordHash}, #{nickname}, 'ACTIVE')")
    int insert(Account account);

    @Select("SELECT id, username, nickname, avatar, gender, birthday, bio FROM account WHERE id=#{id}")
    @ConstructorArgs({@Arg(column="id", javaType=Long.class), @Arg(column="username", javaType=String.class), @Arg(column="nickname", javaType=String.class), @Arg(column="avatar", javaType=String.class), @Arg(column="gender", javaType=String.class), @Arg(column="birthday", javaType=java.time.LocalDate.class), @Arg(column="bio", javaType=String.class)})
    AccountProfile findProfileById(long id);

    @Update("UPDATE account SET nickname=#{nickname}, gender=#{gender}, birthday=#{birthday}, bio=#{bio} WHERE id=#{id}")
    int updateProfile(@Param("id") long id, @Param("nickname") String nickname, @Param("gender") String gender, @Param("birthday") java.time.LocalDate birthday, @Param("bio") String bio);

    @Update("UPDATE account SET avatar=#{avatar} WHERE id=#{id}")
    int updateAvatar(@Param("id") long id, @Param("avatar") String avatar);
}
