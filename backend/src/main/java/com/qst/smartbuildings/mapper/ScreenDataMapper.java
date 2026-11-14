package com.qst.smartbuildings.mapper;

import com.qst.smartbuildings.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScreenDataMapper {
    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN online_status=1 AND enable_status=1 THEN 1 ELSE 0 END),0) onlineCount, " +
            "COALESCE(SUM(CASE WHEN online_status=0 AND enable_status=1 THEN 1 ELSE 0 END),0) offlineCount, " +
            "COALESCE(SUM(CASE WHEN enable_status=1 AND online_status=0 THEN 1 ELSE 0 END),0) faultCount " +
            "FROM asset_device WHERE is_deleted=0")
    DeviceStatusOverview selectDeviceStatus();

    @Select("SELECT e.id alarmId, e.metric_type alarmType, " +
            "CASE WHEN e.status=2 THEN 'alarm' ELSE 'warning' END alarmLevel, " +
            "p.id deviceId, p.name deviceName, e.collect_time alarmTime " +
            "FROM biz_env_monitor_data e " +
            "JOIN asset_device_sensor s ON s.id=e.parent_id AND s.is_deleted=0 " +
            "JOIN biz_env_monitor p ON p.id=s.parent_id " +
            "WHERE e.status IN (1,2) ORDER BY e.collect_time DESC, e.id DESC LIMIT 10")
    List<AlarmSummary> selectRecentAlarms();

    @Select("SELECT DATE(e.collect_time) date, COUNT(*) value FROM biz_env_monitor_data e " +
            "WHERE e.status IN (1,2) AND e.collect_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(e.collect_time) ORDER BY date ASC")
    List<TrendPoint> selectAlarmTrend();

    @Select("SELECT e.metric_type metric, e.metric_value value, e.unit, " +
            "CASE WHEN e.status IN (1,2) THEN true ELSE false END exceeded " +
            "FROM biz_env_monitor_data e " +
            "WHERE e.id IN (SELECT MAX(id) FROM biz_env_monitor_data GROUP BY parent_id, metric_type) " +
            "ORDER BY e.collect_time DESC")
    List<EnvironmentMetric> selectEnvironmentMetrics();

    @Select("SELECT d.id deviceId, d.device_name deviceName, d.online_status onlineStatus, " +
            "s.sensor_name name, COALESCE(e.metric_value,0) value, s.unit unit, " +
            "CASE WHEN e.status IN (1,2) THEN true ELSE false END alarm " +
            "FROM asset_device d JOIN asset_device_sensor s ON s.parent_id=d.id AND s.is_deleted=0 " +
            "LEFT JOIN biz_env_monitor_data e ON e.id=(SELECT MAX(e2.id) FROM biz_env_monitor_data e2 " +
            "WHERE e2.parent_id=s.id AND e2.metric_type=s.monitor_type) " +
            "WHERE d.is_deleted=0 AND d.type_id=#{typeId} ORDER BY d.id, s.id")
    List<java.util.Map<String, Object>> selectEquipmentMetrics(@Param("typeId") Integer typeId);
}
