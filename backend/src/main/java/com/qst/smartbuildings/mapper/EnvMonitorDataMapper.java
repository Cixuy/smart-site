package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.EnvMonitorData;
import com.qst.smartbuildings.vo.EnvDailyStatVO;
import com.qst.smartbuildings.vo.EnvMonitorRecordVO;
import com.qst.smartbuildings.vo.EnvTrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EnvMonitorDataMapper extends BaseMapper<EnvMonitorData> {
    String RECORD_COLUMNS = "e.id, e.parent_id AS monitorPointId, p.name AS pointName, "
            + "e.collect_time AS collectTime, e.metric_type AS metricType, e.metric_value AS metricValue, "
            + "e.unit, s.warning_upper AS thresholdWarn, s.alarm_upper AS thresholdAlarm, e.status ";
    String RECORD_JOINS = "FROM biz_env_monitor_data e "
            + "JOIN biz_env_monitor p ON p.id=e.parent_id "
            + "LEFT JOIN asset_device_sensor s ON s.parent_type=2 AND s.parent_id=e.parent_id "
            + "AND s.monitor_type=e.metric_type AND s.is_deleted=0 ";

    @Select("<script>SELECT " + RECORD_COLUMNS + RECORD_JOINS
            + "WHERE e.status IN (1,2) "
            + "<if test='status != null'>AND e.status=#{status} </if>"
            + "<if test='monitorPointId != null'>AND e.parent_id=#{monitorPointId} </if>"
            + "<if test='metricType != null and metricType != &quot;&quot;'>AND e.metric_type=#{metricType} </if>"
            + "<if test='startTime != null'>AND e.collect_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND e.collect_time <![CDATA[ <= ]]> #{endTime} </if>"
            + "ORDER BY e.collect_time DESC, e.id DESC LIMIT #{offset}, #{limit}</script>")
    List<EnvMonitorRecordVO> selectAlarms(@Param("status") Integer status,
                                          @Param("monitorPointId") Long monitorPointId,
                                          @Param("metricType") String metricType,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("offset") long offset,
                                          @Param("limit") long limit);

    @Select("<script>SELECT COUNT(*) FROM biz_env_monitor_data e WHERE e.status IN (1,2) "
            + "<if test='status != null'>AND e.status=#{status} </if>"
            + "<if test='monitorPointId != null'>AND e.parent_id=#{monitorPointId} </if>"
            + "<if test='metricType != null and metricType != &quot;&quot;'>AND e.metric_type=#{metricType} </if>"
            + "<if test='startTime != null'>AND e.collect_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND e.collect_time <![CDATA[ <= ]]> #{endTime} </if>"
            + "</script>")
    long countAlarms(@Param("status") Integer status, @Param("monitorPointId") Long monitorPointId,
                     @Param("metricType") String metricType, @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT " + RECORD_COLUMNS + RECORD_JOINS
            + "WHERE NOT EXISTS (SELECT 1 FROM biz_env_monitor_data newer "
            + "WHERE newer.parent_id=e.parent_id AND newer.metric_type=e.metric_type "
            + "AND (newer.collect_time > e.collect_time OR (newer.collect_time=e.collect_time AND newer.id > e.id))) "
            + "<if test='monitorPointId != null'>AND e.parent_id=#{monitorPointId} </if>"
            + "<if test='metricType != null and metricType != &quot;&quot;'>AND e.metric_type=#{metricType} </if>"
            + "ORDER BY e.collect_time DESC, e.id DESC</script>")
    List<EnvMonitorRecordVO> selectLatest(@Param("monitorPointId") Long monitorPointId,
                                          @Param("metricType") String metricType);

    @Select("<script>SELECT " + RECORD_COLUMNS + RECORD_JOINS
            + "WHERE e.parent_id=#{monitorPointId} AND e.metric_type=#{metricType} "
            + "AND e.collect_time <![CDATA[ >= ]]> #{startTime} AND e.collect_time <![CDATA[ <= ]]> #{endTime} "
            + "ORDER BY e.collect_time ASC, e.id ASC LIMIT #{offset}, #{limit}</script>")
    List<EnvMonitorRecordVO> selectHistory(@Param("monitorPointId") Long monitorPointId,
                                           @Param("metricType") String metricType,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("offset") long offset, @Param("limit") long limit);

    @Select("SELECT COUNT(*) FROM biz_env_monitor_data WHERE parent_id=#{monitorPointId} "
            + "AND metric_type=#{metricType} AND collect_time >= #{startTime} AND collect_time <= #{endTime}")
    long countHistory(@Param("monitorPointId") Long monitorPointId, @Param("metricType") String metricType,
                      @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT d.id, d.monitor_point_id AS `monitorPointId`, p.name AS `pointName`, "
            + "d.stat_date AS `statDate`, d.metric_type AS `metricType`, d.max_value AS `maxValue`, "
            + "d.min_value AS `minValue`, d.avg_value AS `avgValue`, d.alarm_count AS `alarmCount`, d.warn_count AS `warnCount` "
            + "FROM biz_env_daily_stat d JOIN biz_env_monitor p ON p.id=d.monitor_point_id WHERE 1=1 "
            + "<if test='monitorPointId != null'>AND d.monitor_point_id=#{monitorPointId} </if>"
            + "<if test='metricType != null and metricType != &quot;&quot;'>AND d.metric_type=#{metricType} </if>"
            + "<if test='startDate != null'>AND d.stat_date <![CDATA[ >= ]]> #{startDate} </if>"
            + "<if test='endDate != null'>AND d.stat_date <![CDATA[ <= ]]> #{endDate} </if>"
            + "ORDER BY d.stat_date DESC, d.monitor_point_id, d.metric_type LIMIT #{offset}, #{limit}</script>")
    List<EnvDailyStatVO> selectDailyStats(@Param("monitorPointId") Long monitorPointId,
                                          @Param("metricType") String metricType,
                                          @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                          @Param("offset") long offset, @Param("limit") long limit);

    @Select("<script>SELECT COUNT(*) FROM biz_env_daily_stat d WHERE 1=1 "
            + "<if test='monitorPointId != null'>AND d.monitor_point_id=#{monitorPointId} </if>"
            + "<if test='metricType != null and metricType != &quot;&quot;'>AND d.metric_type=#{metricType} </if>"
            + "<if test='startDate != null'>AND d.stat_date <![CDATA[ >= ]]> #{startDate} </if>"
            + "<if test='endDate != null'>AND d.stat_date <![CDATA[ <= ]]> #{endDate} </if>"
            + "</script>")
    long countDailyStats(@Param("monitorPointId") Long monitorPointId, @Param("metricType") String metricType,
                         @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Results(id = "envTrendPointMapping", value = {
            @Result(column = "trend_date", property = "date"),
            @Result(column = "max_value", property = "maxValue"),
            @Result(column = "min_value", property = "minValue"),
            @Result(column = "avg_value", property = "avgValue")
    })
    @Select("SELECT stat_date AS trend_date, max_value, min_value, avg_value FROM biz_env_daily_stat "
            + "WHERE monitor_point_id=#{monitorPointId} AND metric_type=#{metricType} "
            + "AND stat_date BETWEEN #{startDate} AND #{endDate} ORDER BY stat_date ASC")
    List<EnvTrendPointVO> selectTrend(@Param("monitorPointId") Long monitorPointId,
                                      @Param("metricType") String metricType,
                                      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
