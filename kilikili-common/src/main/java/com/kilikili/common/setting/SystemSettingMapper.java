package com.kilikili.common.setting;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemSettingMapper {
    @Select("SELECT setting_key, setting_value FROM system_setting")
    @ConstructorArgs({
            @Arg(column = "setting_key", javaType = String.class),
            @Arg(column = "setting_value", javaType = String.class)
    })
    List<SystemSettingEntry> list();

    @Select("SELECT setting_value FROM system_setting WHERE setting_key = #{key}")
    String get(@Param("key") String key);

    @Insert("""
            INSERT INTO system_setting(setting_key, setting_value, description, updated_by)
            VALUES(#{key}, #{value}, #{description}, #{updatedBy})
            ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),
                                    description=VALUES(description),
                                    updated_by=VALUES(updated_by),
                                    updated_at=CURRENT_TIMESTAMP
            """)
    int upsert(@Param("key") String key,
               @Param("value") String value,
               @Param("description") String description,
               @Param("updatedBy") Long updatedBy);
}
