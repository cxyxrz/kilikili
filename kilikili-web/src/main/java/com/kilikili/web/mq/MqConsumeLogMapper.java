package com.kilikili.web.mq;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MqConsumeLogMapper {
    @Select("SELECT COUNT(*) FROM mq_consume_log WHERE msg_id=#{msgId}")
    int exists(@Param("msgId") String msgId);

    /** 主键冲突说明该消息已被成功消费过，返回 0 表示本次为重复投递 */
    @Insert("INSERT IGNORE INTO mq_consume_log(msg_id, topic, stage, video_id) "
            + "VALUES(#{msgId}, #{topic}, #{stage}, #{videoId})")
    int markConsumed(@Param("msgId") String msgId, @Param("topic") String topic,
                     @Param("stage") String stage, @Param("videoId") long videoId);

    @Select("SELECT COUNT(*) FROM mq_consume_log")
    long total();
}
