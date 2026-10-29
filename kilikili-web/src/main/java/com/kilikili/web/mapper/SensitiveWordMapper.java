package com.kilikili.web.mapper;

import com.kilikili.web.entity.SensitiveWord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SensitiveWordMapper {

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "word", javaType = String.class),
            @Arg(column = "action", javaType = String.class),
            @Arg(column = "enabled", javaType = Boolean.class)
    })
    @Select("SELECT id,word,action,enabled FROM sensitive_word WHERE enabled=1")
    List<SensitiveWord> listEnabled();
}
