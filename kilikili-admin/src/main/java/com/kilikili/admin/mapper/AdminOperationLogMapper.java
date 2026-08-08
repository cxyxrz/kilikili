package com.kilikili.admin.mapper;

import com.kilikili.admin.entity.AdminOperationLogItem;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminOperationLogMapper {
    @Insert("""
            INSERT INTO admin_operation_log(
              operator_id, operator_name, module, action, description,
              ip_address, status, error_message, duration_ms
            ) VALUES(
              #{operatorId}, #{operatorName}, #{module}, #{action}, #{description},
              #{ipAddress}, #{status}, #{errorMessage}, #{durationMs}
            )
            """)
    int insert(@Param("operatorId") Long operatorId,
               @Param("operatorName") String operatorName,
               @Param("module") String module,
               @Param("action") String action,
               @Param("description") String description,
               @Param("ipAddress") String ipAddress,
               @Param("status") String status,
               @Param("errorMessage") String errorMessage,
               @Param("durationMs") long durationMs);

    @Select("""
            <script>
            SELECT id, operator_id, operator_name, module, action, description,
                   ip_address, status, error_message, duration_ms, created_at
            FROM admin_operation_log
            <where>
              <if test='operator != null and operator != ""'>
                AND operator_name LIKE CONCAT('%', #{operator}, '%')
              </if>
              <if test='module != null and module != ""'>AND module = #{module}</if>
            </where>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "operator_id", javaType = Long.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "module", javaType = String.class),
            @Arg(column = "action", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "ip_address", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "error_message", javaType = String.class),
            @Arg(column = "duration_ms", javaType = Long.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    List<AdminOperationLogItem> list(@Param("operator") String operator,
                                     @Param("module") String module,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM admin_operation_log
            <where>
              <if test='operator != null and operator != ""'>
                AND operator_name LIKE CONCAT('%', #{operator}, '%')
              </if>
              <if test='module != null and module != ""'>AND module = #{module}</if>
            </where>
            </script>
            """)
    long count(@Param("operator") String operator, @Param("module") String module);

    @Delete("DELETE FROM admin_operation_log")
    int deleteAll();
}
