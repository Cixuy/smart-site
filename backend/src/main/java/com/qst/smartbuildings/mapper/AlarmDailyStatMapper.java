package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AlarmDailyStat;
import com.qst.smartbuildings.vo.DailyAlarmStatVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AlarmDailyStatMapper extends BaseMapper<AlarmDailyStat> {
    @Select("SELECT stat_date AS statDate, source_type AS sourceType, alarm_level AS alarmLevel, "
            + "total_count AS totalCount, pending_count AS pendingCount, resolved_count AS resolvedCount, "
            + "false_alarm_count AS falseAlarmCount, avg_disposal_minutes AS avgDisposalMinutes, "
            + "max_disposal_minutes AS maxDisposalMinutes FROM biz_alarm_daily_stat "
            + "WHERE project_id=#{projectId} AND stat_date=#{statDate} ORDER BY alarm_level ASC,source_type ASC")
    List<DailyAlarmStatVO> selectByProjectAndDate(@Param("projectId") Long projectId,
                                                  @Param("statDate") LocalDate statDate);

    @Delete("DELETE FROM biz_alarm_daily_stat WHERE project_id=#{projectId} AND stat_date=#{statDate}")
    int deleteByProjectAndDate(@Param("projectId") Long projectId, @Param("statDate") LocalDate statDate);

    @Insert("INSERT INTO biz_alarm_daily_stat "
            + "(project_id,stat_date,alarm_level,source_type,total_count,pending_count,resolved_count,"
            + "false_alarm_count,avg_disposal_minutes,max_disposal_minutes) "
            + "SELECT a.project_id,DATE(a.alarm_time),a.alarm_level,a.source_type,COUNT(*),"
            + "SUM(CASE WHEN a.status=0 THEN 1 ELSE 0 END),SUM(CASE WHEN a.status=2 THEN 1 ELSE 0 END),"
            + "SUM(CASE WHEN EXISTS(SELECT 1 FROM biz_alarm_disposal fd WHERE fd.alarm_id=a.id "
            + "AND fd.disposal_conclusion=2) THEN 1 ELSE 0 END),"
            + "ROUND(AVG(CASE WHEN d.disposal_time IS NULL THEN NULL ELSE TIMESTAMPDIFF(SECOND,a.alarm_time,d.disposal_time)/60 END),2),"
            + "ROUND(MAX(CASE WHEN d.disposal_time IS NULL THEN NULL ELSE TIMESTAMPDIFF(SECOND,a.alarm_time,d.disposal_time)/60 END),2) "
            + "FROM biz_alarm_record a LEFT JOIN biz_alarm_disposal d ON d.id=("
            + "SELECT d1.id FROM biz_alarm_disposal d1 WHERE d1.alarm_id=a.id "
            + "ORDER BY d1.disposal_time ASC,d1.id ASC LIMIT 1) "
            + "WHERE a.project_id=#{projectId} AND a.is_deleted=0 "
            + "AND a.alarm_time>=#{startTime} AND a.alarm_time<#{endTime} "
            + "GROUP BY a.project_id,DATE(a.alarm_time),a.alarm_level,a.source_type")
    int insertAggregate(@Param("projectId") Long projectId,
                        @Param("startTime") java.time.LocalDateTime startTime,
                        @Param("endTime") java.time.LocalDateTime endTime);
}