package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AlarmDisposal;
import com.qst.smartbuildings.vo.DisposalRecordVO;
import com.qst.smartbuildings.vo.OperatorTimelinessVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlarmDisposalMapper extends BaseMapper<AlarmDisposal> {
    String COLUMNS = "id, alarm_id AS alarmId, operator_id AS operatorId, operator_name AS operatorName, "
            + "disposal_content AS disposalContent, disposal_conclusion AS disposalConclusion, "
            + "false_reason AS falseReason, disposal_time AS disposalTime";

    @Select("SELECT " + COLUMNS + " FROM biz_alarm_disposal WHERE alarm_id=#{alarmId} "
            + "ORDER BY disposal_time ASC, id ASC")
    List<DisposalRecordVO> selectHistory(@Param("alarmId") Long alarmId);

    @Select("SELECT COUNT(*) FROM biz_alarm_disposal WHERE alarm_id=#{alarmId}")
    long countPage(@Param("alarmId") Long alarmId);

    @Select("SELECT " + COLUMNS + " FROM biz_alarm_disposal WHERE alarm_id=#{alarmId} "
            + "ORDER BY disposal_time DESC, id DESC LIMIT #{offset},#{limit}")
    List<DisposalRecordVO> selectPage(@Param("alarmId") Long alarmId, @Param("offset") long offset,
                                      @Param("limit") long limit);

    @Select("<script>SELECT first_disposal.operator_id AS operatorId, first_disposal.operator_name AS operatorName, "
            + "COUNT(*) AS disposedCount, "
            + "SUM(CASE WHEN TIMESTAMPDIFF(SECOND, a.alarm_time, first_disposal.disposal_time) <![CDATA[ <= ]]> #{deadlineSeconds} THEN 1 ELSE 0 END) AS timelyCount, "
            + "SUM(CASE WHEN TIMESTAMPDIFF(SECOND, a.alarm_time, first_disposal.disposal_time) > #{deadlineSeconds} THEN 1 ELSE 0 END) AS overdueCount, "
            + "ROUND(AVG(TIMESTAMPDIFF(SECOND, a.alarm_time, first_disposal.disposal_time))/60,2) AS avgDisposalMinutes, "
            + "ROUND(MAX(TIMESTAMPDIFF(SECOND, a.alarm_time, first_disposal.disposal_time))/60,2) AS maxDisposalMinutes "
            + "FROM biz_alarm_record a JOIN biz_alarm_disposal first_disposal ON first_disposal.id=("
            + "SELECT d.id FROM biz_alarm_disposal d WHERE d.alarm_id=a.id ORDER BY d.disposal_time ASC,d.id ASC LIMIT 1) "
            + "WHERE a.is_deleted=0 AND a.project_id=#{projectId} AND a.alarm_time BETWEEN #{startTime} AND #{endTime} "
            + "<if test='operatorId != null'>AND first_disposal.operator_id=#{operatorId} </if>"
            + "GROUP BY first_disposal.operator_id,first_disposal.operator_name "
            + "ORDER BY disposedCount DESC,first_disposal.operator_id ASC</script>")
    List<OperatorTimelinessVO> selectTimeliness(@Param("projectId") Long projectId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("deadlineSeconds") long deadlineSeconds,
                                                @Param("operatorId") Long operatorId);
}