package com.kilikili.web.mq;

import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MqDeadLetterMapper {
    String COLUMNS = "id, msg_id, topic, stage, video_id, payload, error, reconsume_times, "
            + "status, created_at, replayed_at ";

    @Insert("INSERT IGNORE INTO mq_dead_letter(msg_id, topic, stage, video_id, payload, error, reconsume_times) "
            + "VALUES(#{msgId}, #{topic}, #{stage}, #{videoId}, #{payload}, #{error}, #{reconsumeTimes})")
    int insert(@Param("msgId") String msgId, @Param("topic") String topic, @Param("stage") String stage,
               @Param("videoId") long videoId, @Param("payload") String payload,
               @Param("error") String error, @Param("reconsumeTimes") int reconsumeTimes);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "msg_id", javaType = String.class),
            @Arg(column = "topic", javaType = String.class),
            @Arg(column = "stage", javaType = String.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "payload", javaType = String.class),
            @Arg(column = "error", javaType = String.class),
            @Arg(column = "reconsume_times", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "replayed_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("SELECT " + COLUMNS + "FROM mq_dead_letter WHERE id=#{id}")
    DeadLetter findById(long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "msg_id", javaType = String.class),
            @Arg(column = "topic", javaType = String.class),
            @Arg(column = "stage", javaType = String.class),
            @Arg(column = "video_id", javaType = Long.class),
            @Arg(column = "payload", javaType = String.class),
            @Arg(column = "error", javaType = String.class),
            @Arg(column = "reconsume_times", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "replayed_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("SELECT " + COLUMNS + "FROM mq_dead_letter "
            + "WHERE (#{status} IS NULL OR status=#{status}) ORDER BY id DESC LIMIT #{limit}")
    List<DeadLetter> list(@Param("status") String status, @Param("limit") int limit);

    @Update("UPDATE mq_dead_letter SET status='REPLAYED', replayed_at=NOW() WHERE id=#{id}")
    int markReplayed(long id);

    @Select("SELECT COUNT(*) FROM mq_dead_letter WHERE status='NEW'")
    long countNew();
}
