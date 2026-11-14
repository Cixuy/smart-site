package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.vo.SensorMetaVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetDeviceSensorMapper extends BaseMapper<AssetDeviceSensor> {
    @Select("SELECT d.device_code FROM asset_device_sensor s "
            + "INNER JOIN asset_device d ON d.id = s.parent_id "
            + "WHERE s.id = #{sensorId} AND s.is_deleted = 0 AND d.is_deleted = 0")
    String selectDeviceCodeBySensorId(@Param("sensorId") Long sensorId);

    @Select("SELECT d.device_name FROM asset_device_sensor s "
            + "LEFT JOIN asset_device d ON d.id = s.parent_id AND d.is_deleted = 0 "
            + "WHERE s.id = #{sensorId} AND s.is_deleted = 0")
    String selectParentName(@Param("sensorId") Long sensorId);

    /** 校验设备（父级类型=1）是否存在且未删除 */
    @Select("SELECT COUNT(*) FROM asset_device WHERE id = #{parentId} AND is_deleted = 0")
    boolean existsActiveDevice(@Param("parentId") Long parentId);

    /** 校验环境监测点（父级类型=2）是否存在 */
    @Select("SELECT COUNT(*) FROM biz_env_monitor WHERE id = #{parentId}")
    boolean existsEnvMonitorPoint(@Param("parentId") Long parentId);

    /**
     * 查询传感器元数据列表。parent_type 由父节点所在表推导：
     * 父ID存在于 asset_device（且未删除）时为 1-设备，存在于 biz_env_monitor 时为 2-监测点。
     */
    @Select("<script>"
            + "SELECT s.id, s.sensor_name AS sensorName, s.monitor_type AS monitorType, s.unit, "
            + "s.parent_id AS parentId, "
            + "CASE WHEN d.id IS NOT NULL THEN 1 WHEN p.id IS NOT NULL THEN 2 ELSE 0 END AS parentType, "
            + "s.warning_upper AS warningUpper, s.warning_lower AS warningLower, "
            + "s.alarm_upper AS alarmUpper, s.alarm_lower AS alarmLower, "
            + "s.refresh_rate AS refreshRate, s.status "
            + "FROM asset_device_sensor s "
            + "LEFT JOIN asset_device d ON d.id = s.parent_id AND d.is_deleted = 0 "
            + "LEFT JOIN biz_env_monitor p ON p.id = s.parent_id "
            + "WHERE s.is_deleted = 0 "
            + "<if test='parentId != null'>AND s.parent_id = #{parentId} </if>"
            + "<if test='parentType != null'>AND CASE WHEN d.id IS NOT NULL THEN 1 WHEN p.id IS NOT NULL THEN 2 ELSE 0 END = #{parentType} </if>"
            + "ORDER BY s.id"
            + "</script>")
    List<SensorMetaVO> selectSensorMeta(@Param("parentId") Long parentId, @Param("parentType") Integer parentType);
}
