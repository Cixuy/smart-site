package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AlarmRecovery;
import com.qst.smartbuildings.vo.RecoveryRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlarmRecoveryMapper extends BaseMapper<AlarmRecovery> {
    @Select("SELECT id, alarm_id AS alarmId, recovery_time AS recoveryTime, "
            + "recovery_value AS recoveryValue, recovery_note AS recoveryNote "
            + "FROM biz_alarm_recovery WHERE alarm_id=#{alarmId}")
    RecoveryRecordVO selectHistory(@Param("alarmId") Long alarmId);

    @Select("SELECT id FROM biz_alarm_recovery WHERE alarm_id=#{alarmId}")
    Long selectIdByAlarmId(@Param("alarmId") Long alarmId);
}