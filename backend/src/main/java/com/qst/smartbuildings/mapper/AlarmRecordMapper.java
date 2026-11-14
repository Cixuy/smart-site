package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AlarmRecord;
import com.qst.smartbuildings.vo.AlarmCountVO;
import com.qst.smartbuildings.vo.AlarmDistributionItemVO;
import com.qst.smartbuildings.vo.AlarmListVO;
import com.qst.smartbuildings.vo.AlarmTrendPointVO;
import com.qst.smartbuildings.vo.AiAlarmDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {
    String COLUMNS = "a.id, a.alarm_no AS alarmNo, a.project_id AS projectId, a.source_type AS sourceType, "
            + "CASE a.source_type WHEN 1 THEN '设备监测告警' WHEN 2 THEN '环境监测告警' WHEN 3 THEN 'AI识别告警' END AS sourceTypeText, "
            + "a.alarm_level AS alarmLevel, CASE a.alarm_level WHEN 1 THEN '预警' WHEN 2 THEN '警报' WHEN 3 THEN '控制' END AS alarmLevelText, "
            + "a.alarm_type_code AS alarmTypeCode, "
            + "CASE a.alarm_type_code WHEN 'OVERLOAD' THEN '超载' WHEN 'MOMENT_EXCEED' THEN '力矩超标' "
            + "WHEN 'WIND_EXCEED' THEN '风速超标' WHEN 'LOAD_EXCEED' THEN '吊重超标' WHEN 'DOOR_OPEN' THEN '双门同开' "
            + "WHEN 'PM25_EXCEED' THEN 'PM2.5超标' WHEN 'PM10_EXCEED' THEN 'PM10超标' WHEN 'NOISE_EXCEED' THEN '噪声超标' "
            + "WHEN 'NO_HELMET' THEN '未佩戴安全帽' WHEN 'NO_VEST' THEN '未穿安全服' WHEN 'SMOKING' THEN '现场吸烟' "
            + "WHEN 'FIRE' THEN '现场明火' ELSE a.alarm_type_code END AS alarmTypeText, "
            + "a.alarm_content AS alarmContent, a.current_value AS currentValue, a.threshold_range AS thresholdRange, "
            + "a.status, CASE a.status WHEN 0 THEN '未处置' WHEN 1 THEN '处置中' WHEN 2 THEN '已处置' END AS statusText, "
            + "d.type_id AS deviceTypeId, dt.type_name AS deviceTypeName, a.device_id AS deviceId, d.device_name AS deviceName, "
            + "a.monitor_point_id AS monitorPointId, s.parent_type AS monitorPointParentType, s.sensor_name AS monitorPointName, a.camera_id AS cameraId, "
            + "c.camera_name AS cameraName, a.related_alarm_id AS relatedAlarmId, "
            + "(SELECT COUNT(*)-1 FROM biz_alarm_record x WHERE x.is_deleted=0 AND "
            + "(x.id=COALESCE(a.related_alarm_id,a.id) OR x.related_alarm_id=COALESCE(a.related_alarm_id,a.id))) AS relatedCount, "
            + "a.alarm_time AS alarmTime, CASE WHEN r.id IS NULL THEN FALSE ELSE TRUE END AS recovered, r.recovery_time AS recoveryTime";

    String JOINS = " FROM biz_alarm_record a "
            + "LEFT JOIN asset_device d ON d.id=a.device_id AND d.is_deleted=0 "
            + "LEFT JOIN asset_device_type dt ON dt.id=d.type_id AND dt.is_deleted=0 "
            + "LEFT JOIN asset_device_sensor s ON s.id=a.monitor_point_id AND s.is_deleted=0 "
            + "LEFT JOIN biz_camera c ON c.id=a.camera_id AND c.is_deleted=0 "
            + "LEFT JOIN biz_alarm_recovery r ON r.alarm_id=a.id ";

    @Select("<script>SELECT " + COLUMNS + JOINS + "WHERE a.is_deleted=0 AND a.source_type=3 "
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='alarmTypeCode != null and alarmTypeCode != &quot;&quot;'>AND a.alarm_type_code=#{alarmTypeCode} </if>"
            + "<if test='startTime != null'>AND a.alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND a.alarm_time <![CDATA[ <= ]]> #{endTime} </if>"
            + "ORDER BY a.alarm_time DESC, a.id DESC LIMIT #{offset},#{limit}</script>")
    List<AlarmListVO> selectAiPage(@Param("status") Integer status,
                                   @Param("alarmTypeCode") String alarmTypeCode,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("offset") long offset, @Param("limit") long limit);

    @Select("<script>SELECT COUNT(*) FROM biz_alarm_record a WHERE a.is_deleted=0 AND a.source_type=3 "
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='alarmTypeCode != null and alarmTypeCode != &quot;&quot;'>AND a.alarm_type_code=#{alarmTypeCode} </if>"
            + "<if test='startTime != null'>AND a.alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND a.alarm_time <![CDATA[ <= ]]> #{endTime} </if></script>")
    long countAiPage(@Param("status") Integer status, @Param("alarmTypeCode") String alarmTypeCode,
                     @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT " + COLUMNS + JOINS + "WHERE a.id=#{id} AND a.source_type=3 AND a.is_deleted=0")
    AiAlarmDetailVO selectAiDetail(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + JOINS + "WHERE a.id=#{id} AND a.is_deleted=0")
    AlarmListVO selectDetail(@Param("id") Long id);

    @Update("UPDATE biz_alarm_record SET is_deleted=1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND is_deleted=0")
    int logicalDelete(@Param("id") Long id);

    @Update("UPDATE biz_alarm_record SET status=#{status}, updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND is_deleted=0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE biz_alarm_record SET status=1, updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status=0 AND is_deleted=0")
    int markHandling(@Param("id") Long id);

    @Select("SELECT id FROM biz_alarm_record WHERE is_deleted=0 AND status IN (0,1) "
            + "AND source_type=#{sourceType} AND monitor_point_id=#{monitorPointId} "
            + "AND NOT EXISTS (SELECT 1 FROM biz_alarm_recovery r WHERE r.alarm_id=biz_alarm_record.id) "
            + "ORDER BY alarm_time DESC,id DESC")
    List<Long> selectRecoverableByMonitorPoint(@Param("sourceType") Integer sourceType,
                                               @Param("monitorPointId") Long monitorPointId);

    @Select("<script>SELECT " + COLUMNS + JOINS + "WHERE a.is_deleted=0 "
            + "<if test='projectId != null'>AND a.project_id=#{projectId} </if>"
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "<if test='deviceTypeId != null'>AND d.type_id=#{deviceTypeId} </if>"
            + "<if test='alarmLevel != null'>AND a.alarm_level=#{alarmLevel} </if>"
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='alarmTypeCode != null'>AND a.alarm_type_code=#{alarmTypeCode} </if>"
            + "<if test='deviceId != null'>AND a.device_id=#{deviceId} </if>"
            + "<if test='monitorPointId != null'>AND a.monitor_point_id=#{monitorPointId} </if>"
            + "<if test='cameraId != null'>AND a.camera_id=#{cameraId} </if>"
            + "<if test='relatedAlarmId != null'>AND (a.id=#{relatedAlarmId} OR a.related_alarm_id=#{relatedAlarmId}) </if>"
            + "<if test='keyword != null'>AND (a.alarm_no LIKE CONCAT('%',#{keyword},'%') OR a.alarm_content LIKE CONCAT('%',#{keyword},'%') "
            + "OR d.device_name LIKE CONCAT('%',#{keyword},'%') OR s.sensor_name LIKE CONCAT('%',#{keyword},'%') OR c.camera_name LIKE CONCAT('%',#{keyword},'%')) </if>"
            + "<if test='startTime != null'>AND a.alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND a.alarm_time <![CDATA[ <= ]]> #{endTime} </if>"
            + "ORDER BY CASE WHEN a.status=0 THEN 0 ELSE 1 END,a.alarm_level DESC,a.alarm_time DESC,a.id DESC "
            + "LIMIT #{offset},#{limit}</script>")
    List<AlarmListVO> selectPage(@Param("projectId") Long projectId, @Param("sourceType") Integer sourceType,
            @Param("deviceTypeId") Long deviceTypeId, @Param("alarmLevel") Integer alarmLevel,
            @Param("status") Integer status, @Param("alarmTypeCode") String alarmTypeCode,
            @Param("deviceId") Long deviceId, @Param("monitorPointId") Long monitorPointId,
            @Param("cameraId") Long cameraId, @Param("relatedAlarmId") Long relatedAlarmId,
            @Param("keyword") String keyword, @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime, @Param("offset") long offset, @Param("limit") long limit);

    @Select("<script>SELECT COUNT(*) " + JOINS + "WHERE a.is_deleted=0 "
            + "<if test='projectId != null'>AND a.project_id=#{projectId} </if>"
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "<if test='deviceTypeId != null'>AND d.type_id=#{deviceTypeId} </if>"
            + "<if test='alarmLevel != null'>AND a.alarm_level=#{alarmLevel} </if>"
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='alarmTypeCode != null'>AND a.alarm_type_code=#{alarmTypeCode} </if>"
            + "<if test='deviceId != null'>AND a.device_id=#{deviceId} </if>"
            + "<if test='monitorPointId != null'>AND a.monitor_point_id=#{monitorPointId} </if>"
            + "<if test='cameraId != null'>AND a.camera_id=#{cameraId} </if>"
            + "<if test='relatedAlarmId != null'>AND (a.id=#{relatedAlarmId} OR a.related_alarm_id=#{relatedAlarmId}) </if>"
            + "<if test='keyword != null'>AND (a.alarm_no LIKE CONCAT('%',#{keyword},'%') OR a.alarm_content LIKE CONCAT('%',#{keyword},'%') "
            + "OR d.device_name LIKE CONCAT('%',#{keyword},'%') OR s.sensor_name LIKE CONCAT('%',#{keyword},'%') OR c.camera_name LIKE CONCAT('%',#{keyword},'%')) </if>"
            + "<if test='startTime != null'>AND a.alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND a.alarm_time <![CDATA[ <= ]]> #{endTime} </if></script>")
    long countPage(@Param("projectId") Long projectId, @Param("sourceType") Integer sourceType,
            @Param("deviceTypeId") Long deviceTypeId, @Param("alarmLevel") Integer alarmLevel,
            @Param("status") Integer status, @Param("alarmTypeCode") String alarmTypeCode,
            @Param("deviceId") Long deviceId, @Param("monitorPointId") Long monitorPointId,
            @Param("cameraId") Long cameraId, @Param("relatedAlarmId") Long relatedAlarmId,
            @Param("keyword") String keyword, @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT COUNT(*) FROM biz_alarm_record a WHERE a.is_deleted=0 "
            + "<if test='projectId != null'>AND a.project_id=#{projectId} </if>"
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "<if test='alarmLevel != null'>AND a.alarm_level=#{alarmLevel} </if>"
            + "<if test='alarmTypeCode != null'>AND a.alarm_type_code=#{alarmTypeCode} </if>"
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='deviceId != null'>AND a.device_id=#{deviceId} </if>"
            + "<if test='cameraId != null'>AND a.camera_id=#{cameraId} </if>"
            + "<if test='monitorPointId != null'>AND a.monitor_point_id=#{monitorPointId} </if>"
            + "<if test='alarmNo != null'>AND a.alarm_no=#{alarmNo} </if>"
            + "<if test='alarmContent != null'>AND a.alarm_content LIKE CONCAT('%',#{alarmContent},'%') </if>"
            + "<if test='relatedAlarmId != null'>AND (a.id=#{relatedAlarmId} OR a.related_alarm_id=#{relatedAlarmId}) </if>"
            + "<if test='startTime != null'>AND a.alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND a.alarm_time <![CDATA[ <= ]]> #{endTime} </if></script>")
    long countRecordPage(@Param("projectId") Long projectId, @Param("sourceType") Integer sourceType,
            @Param("alarmLevel") Integer alarmLevel, @Param("alarmTypeCode") String alarmTypeCode,
            @Param("status") Integer status, @Param("deviceId") Long deviceId, @Param("cameraId") Long cameraId,
            @Param("monitorPointId") Long monitorPointId, @Param("alarmNo") String alarmNo,
            @Param("alarmContent") String alarmContent, @Param("relatedAlarmId") Long relatedAlarmId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT " + COLUMNS + JOINS + "WHERE a.is_deleted=0 "
            + "<if test='projectId != null'>AND a.project_id=#{projectId} </if>"
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "<if test='alarmLevel != null'>AND a.alarm_level=#{alarmLevel} </if>"
            + "<if test='alarmTypeCode != null'>AND a.alarm_type_code=#{alarmTypeCode} </if>"
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='deviceId != null'>AND a.device_id=#{deviceId} </if>"
            + "<if test='cameraId != null'>AND a.camera_id=#{cameraId} </if>"
            + "<if test='monitorPointId != null'>AND a.monitor_point_id=#{monitorPointId} </if>"
            + "<if test='alarmNo != null'>AND a.alarm_no=#{alarmNo} </if>"
            + "<if test='alarmContent != null'>AND a.alarm_content LIKE CONCAT('%',#{alarmContent},'%') </if>"
            + "<if test='relatedAlarmId != null'>AND (a.id=#{relatedAlarmId} OR a.related_alarm_id=#{relatedAlarmId}) </if>"
            + "<if test='startTime != null'>AND a.alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND a.alarm_time <![CDATA[ <= ]]> #{endTime} </if>"
            + "ORDER BY ${sortField} ${sortOrder} LIMIT #{offset},#{limit}</script>")
    List<AlarmListVO> selectRecordPage(@Param("projectId") Long projectId, @Param("sourceType") Integer sourceType,
            @Param("alarmLevel") Integer alarmLevel, @Param("alarmTypeCode") String alarmTypeCode,
            @Param("status") Integer status, @Param("deviceId") Long deviceId, @Param("cameraId") Long cameraId,
            @Param("monitorPointId") Long monitorPointId, @Param("alarmNo") String alarmNo,
            @Param("alarmContent") String alarmContent, @Param("relatedAlarmId") Long relatedAlarmId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("sortField") String sortField, @Param("sortOrder") String sortOrder,
            @Param("offset") long offset, @Param("limit") long limit);

    @Select("SELECT " + COLUMNS + JOINS + "WHERE a.is_deleted=0 AND "
            + "(a.id=COALESCE((SELECT related_alarm_id FROM biz_alarm_record WHERE id=#{id}),#{id}) "
            + "OR a.related_alarm_id=COALESCE((SELECT related_alarm_id FROM biz_alarm_record WHERE id=#{id}),#{id})) "
            + "ORDER BY a.alarm_time ASC,a.id ASC")
    List<AlarmListVO> selectRelated(@Param("id") Long id);

    @Select("<script>SELECT status AS dimension,COUNT(*) AS count FROM biz_alarm_record WHERE is_deleted=0 "
            + "<if test='projectId != null'>AND project_id=#{projectId} </if>"
            + "<if test='startTime != null'>AND alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND alarm_time <![CDATA[ <= ]]> #{endTime} </if>GROUP BY status</script>")
    List<AlarmCountVO> countByStatus(@Param("projectId") Long projectId, @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT alarm_level AS dimension,COUNT(*) AS count FROM biz_alarm_record WHERE is_deleted=0 "
            + "<if test='projectId != null'>AND project_id=#{projectId} </if>"
            + "<if test='startTime != null'>AND alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND alarm_time <![CDATA[ <= ]]> #{endTime} </if>GROUP BY alarm_level</script>")
    List<AlarmCountVO> countByLevel(@Param("projectId") Long projectId, @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT source_type AS dimension,COUNT(*) AS count FROM biz_alarm_record WHERE is_deleted=0 "
            + "<if test='projectId != null'>AND project_id=#{projectId} </if>"
            + "<if test='startTime != null'>AND alarm_time <![CDATA[ >= ]]> #{startTime} </if>"
            + "<if test='endTime != null'>AND alarm_time <![CDATA[ <= ]]> #{endTime} </if>GROUP BY source_type</script>")
    List<AlarmCountVO> countBySource(@Param("projectId") Long projectId, @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    @Select("<script>SELECT <choose><when test='granularity == &quot;WEEK&quot;'>DATE_SUB(stat_date,INTERVAL WEEKDAY(stat_date) DAY)"
            + "</when><when test='granularity == &quot;MONTH&quot;'>DATE_SUB(stat_date,INTERVAL DAYOFMONTH(stat_date)-1 DAY)"
            + "</when><otherwise>stat_date</otherwise></choose> AS periodStart, SUM(total_count) AS totalCount, "
            + "SUM(CASE WHEN alarm_level=1 THEN total_count ELSE 0 END) AS warningCount, "
            + "SUM(CASE WHEN alarm_level=2 THEN total_count ELSE 0 END) AS alarmCount, "
            + "SUM(CASE WHEN alarm_level=3 THEN total_count ELSE 0 END) AS controlCount, "
            + "SUM(pending_count) AS pendingCount,SUM(resolved_count) AS resolvedCount "
            + "FROM biz_alarm_daily_stat WHERE project_id=#{projectId} AND stat_date BETWEEN #{startDate} AND #{endDate} "
            + "<if test='sourceType != null'>AND source_type=#{sourceType} </if>"
            + "<if test='alarmLevel != null'>AND alarm_level=#{alarmLevel} </if>"
            + "GROUP BY periodStart ORDER BY periodStart ASC</script>")
    List<AlarmTrendPointVO> selectTrend(@Param("projectId") Long projectId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("granularity") String granularity,
            @Param("sourceType") Integer sourceType, @Param("alarmLevel") Integer alarmLevel);

    @Select("<script>SELECT a.alarm_type_code AS `key`, "
            + "CASE a.alarm_type_code WHEN 'OVERLOAD' THEN '超载' WHEN 'MOMENT_EXCEED' THEN '力矩超标' "
            + "WHEN 'WIND_EXCEED' THEN '风速超标' WHEN 'PM25_EXCEED' THEN 'PM2.5超标' "
            + "WHEN 'PM10_EXCEED' THEN 'PM10超标' WHEN 'NOISE_EXCEED' THEN '噪声超标' "
            + "WHEN 'NO_HELMET' THEN '未佩戴安全帽' ELSE a.alarm_type_code END AS name,COUNT(*) AS count "
            + "FROM biz_alarm_record a WHERE a.is_deleted=0 AND a.project_id=#{projectId} "
            + "AND a.alarm_time BETWEEN #{startTime} AND #{endTime} "
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "GROUP BY a.alarm_type_code ORDER BY count DESC,a.alarm_type_code ASC</script>")
    List<AlarmDistributionItemVO> selectAlarmTypeDistribution(@Param("projectId") Long projectId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("sourceType") Integer sourceType);

    @Select("<script>SELECT COALESCE(CAST(dt.id AS CHAR),'UNKNOWN') AS `key`,COALESCE(dt.type_name,'未关联设备类型') AS name,"
            + "COUNT(*) AS count FROM biz_alarm_record a LEFT JOIN asset_device d ON d.id=a.device_id AND d.is_deleted=0 "
            + "LEFT JOIN asset_device_type dt ON dt.id=d.type_id AND dt.is_deleted=0 "
            + "WHERE a.is_deleted=0 AND a.project_id=#{projectId} AND a.alarm_time BETWEEN #{startTime} AND #{endTime} "
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "GROUP BY dt.id,dt.type_name ORDER BY count DESC,dt.id ASC</script>")
    List<AlarmDistributionItemVO> selectDeviceTypeDistribution(@Param("projectId") Long projectId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("sourceType") Integer sourceType);

    @Select("<script>SELECT " + COLUMNS + JOINS + "WHERE a.is_deleted=0 AND a.project_id=#{projectId} "
            + "AND a.alarm_time BETWEEN #{startTime} AND #{endTime} "
            + "<if test='sourceType != null'>AND a.source_type=#{sourceType} </if>"
            + "<if test='alarmLevel != null'>AND a.alarm_level=#{alarmLevel} </if>"
            + "<if test='status != null'>AND a.status=#{status} </if>"
            + "<if test='operatorId != null'>AND EXISTS(SELECT 1 FROM biz_alarm_disposal od WHERE od.alarm_id=a.id AND od.operator_id=#{operatorId}) </if>"
            + "ORDER BY a.alarm_time DESC,a.id DESC</script>")
    List<AlarmListVO> selectForExport(@Param("projectId") Long projectId, @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime, @Param("sourceType") Integer sourceType,
            @Param("alarmLevel") Integer alarmLevel, @Param("status") Integer status,
            @Param("operatorId") Long operatorId);
}