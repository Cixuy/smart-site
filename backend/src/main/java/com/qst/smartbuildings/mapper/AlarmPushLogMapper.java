package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AlarmPushLog;
import com.qst.smartbuildings.vo.AlarmPushLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AlarmPushLogMapper extends BaseMapper<AlarmPushLog> {
    String COLUMNS = "id, alarm_id AS alarmId, push_channel AS pushChannel, push_target AS pushTarget, "
            + "push_status AS pushStatus, push_time AS pushTime, response_code AS responseCode, "
            + "response_msg AS responseMsg, retry_count AS retryCount, created_at AS createdAt, updated_at AS updatedAt";

    @Select("<script>SELECT COUNT(*) FROM biz_alarm_push_log WHERE alarm_id=#{alarmId} "
            + "<if test='pushStatus != null'>AND push_status=#{pushStatus}</if></script>")
    long countPage(@Param("alarmId") Long alarmId, @Param("pushStatus") Integer pushStatus);

    @Select("<script>SELECT " + COLUMNS + " FROM biz_alarm_push_log WHERE alarm_id=#{alarmId} "
            + "<if test='pushStatus != null'>AND push_status=#{pushStatus} </if>"
            + "ORDER BY created_at DESC,id DESC LIMIT #{offset},#{limit}</script>")
    List<AlarmPushLogVO> selectPage(@Param("alarmId") Long alarmId, @Param("pushStatus") Integer pushStatus,
                                    @Param("offset") long offset, @Param("limit") long limit);

    @Select("SELECT push_status FROM biz_alarm_push_log WHERE alarm_id=#{alarmId} ORDER BY created_at DESC,id DESC LIMIT 1")
    Integer selectLatestStatus(@Param("alarmId") Long alarmId);

    @Update("UPDATE biz_alarm_push_log SET push_status=0, push_time=NULL, response_code=NULL, "
            + "response_msg=NULL, retry_count=retry_count+1, updated_at=CURRENT_TIMESTAMP "
            + "WHERE id=#{id} AND push_status=2")
    int resetFailedForRetry(@Param("id") Long id);
}