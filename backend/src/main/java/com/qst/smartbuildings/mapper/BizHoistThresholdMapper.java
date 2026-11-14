package com.qst.smartbuildings.mapper;

import com.qst.smartbuildings.vo.HoistThresholdVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 升降机告警阈值配置 Mapper
 * 依据重新设计：阈值上下限存储于 asset_device_sensor（parent_id 关联 asset_device.id，
 * monitor_type 存储小写指标名，如 load_weight/load_percent/wind_speed），
 * 接口层面以大写 metricType（LOAD_WEIGHT 等）与预警/报警上下限对外暴露。
 */
@Mapper
public interface BizHoistThresholdMapper {

    /**
     * 查询升降机告警阈值配置（关联 asset_device_sensor + asset_device）
     *
     * @param deviceId 设备编码（为空则返回所有升降机设备阈值配置）
     */
    @Select("<script>"
            + "SELECT d.device_code AS deviceId, UPPER(s.monitor_type) AS metricType, "
            + "       s.warning_upper AS thresholdWarnUpper, s.warning_lower AS thresholdWarnLower, "
            + "       s.alarm_upper AS thresholdAlarmUpper, s.alarm_lower AS thresholdAlarmLower, "
            + "       s.unit, s.update_time AS updateTime "
            + "FROM asset_device_sensor s "
            + "INNER JOIN asset_device d ON d.id = s.parent_id AND d.is_deleted = 0 "
            + "WHERE s.is_deleted = 0 "
            + "  AND s.monitor_type IN ('load_weight','load_percent','wind_speed','height','passenger_count','tilt_angle','door_interlock') "
            + "<if test='deviceId != null and deviceId != \"\"'> AND d.device_code = #{deviceId} </if>"
            + "ORDER BY d.device_code ASC, s.id ASC"
            + "</script>")
    List<HoistThresholdVO> selectThresholds(@Param("deviceId") String deviceId);

    /**
     * 根据设备编码查询设备台账ID（用于校验设备是否存在）
     */
    @Select("SELECT id FROM asset_device WHERE device_code = #{deviceCode} AND is_deleted = 0 LIMIT 1")
    Long selectDeviceIdByCode(@Param("deviceCode") String deviceCode);

    /**
     * 按设备ID + 小写指标名查找已存在的传感器阈值行
     */
    @Select("SELECT id FROM asset_device_sensor "
            + "WHERE parent_id = #{deviceId} AND monitor_type = #{monitorType} AND is_deleted = 0 LIMIT 1")
    Long selectSensorIdByDeviceAndType(@Param("deviceId") Long deviceId,
                                       @Param("monitorType") String monitorType);

    /**
     * 插入新的升降机阈值配置行（sensor_name 由设备编码+指标文本组成）
     */
    @Insert("INSERT INTO asset_device_sensor "
            + "(sensor_name, parent_id, monitor_type, unit, warning_upper, warning_lower, alarm_upper, alarm_lower, "
            + " spray_threshold, refresh_rate, status, is_deleted) "
            + "VALUES (#{sensorName}, #{deviceId}, #{monitorType}, #{unit}, "
            + "        #{warnUpper}, #{warnLower}, #{alarmUpper}, #{alarmLower}, "
            + "        NULL, 5, 1, 0)")
    int insertThreshold(@Param("sensorName") String sensorName,
                        @Param("deviceId") Long deviceId,
                        @Param("monitorType") String monitorType,
                        @Param("unit") String unit,
                        @Param("warnUpper") BigDecimal warnUpper,
                        @Param("warnLower") BigDecimal warnLower,
                        @Param("alarmUpper") BigDecimal alarmUpper,
                        @Param("alarmLower") BigDecimal alarmLower);

    /**
     * 更新已存在的升降机阈值配置行
     */
    @Update("UPDATE asset_device_sensor "
            + "SET unit = #{unit}, "
            + "    warning_upper = #{warnUpper}, warning_lower = #{warnLower}, "
            + "    alarm_upper = #{alarmUpper}, alarm_lower = #{alarmLower} "
            + "WHERE id = #{id} AND is_deleted = 0")
    int updateThresholds(@Param("id") Long id,
                         @Param("unit") String unit,
                         @Param("warnUpper") BigDecimal warnUpper,
                         @Param("warnLower") BigDecimal warnLower,
                         @Param("alarmUpper") BigDecimal alarmUpper,
                         @Param("alarmLower") BigDecimal alarmLower);
}
