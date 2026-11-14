package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.vo.DeviceOfflineLogVO;
import com.qst.smartbuildings.vo.DeviceSensorVO;
import com.qst.smartbuildings.vo.DeviceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账 Mapper，对应表 asset_device。
 */
@Mapper
public interface AssetDeviceMapper extends BaseMapper<Device> {


    /**
     * 分页查询设备列表，关联设备类型与安装位置名称。
     * 支持按设备编码(精确)、设备名称(模糊)、品牌(模糊)、类型集合、位置集合、在线状态、启用状态筛选。
     *
     * @param page          分页参数
     * @param deviceCode    设备编码（精确）
     * @param deviceName    设备名称（模糊）
     * @param serialNos     序号集合（精确）
     */
    @Select("<script>" +
            "SELECT d.id, d.device_code AS deviceCode, d.device_name AS deviceName, " +
            "       d.type_id AS typeId, t.type_name AS typeName, " +
            "       d.location_id AS locationId, l.location_name AS locationName, " +
            "       d.brand, d.model, " +
            "       d.production_date AS productionDate, d.supply_date AS supplyDate, " +
            "       d.acceptance_date AS acceptanceDate, d.install_date AS installDate, " +
            "       d.enable_date AS enableDate, d.expected_scrap_date AS expectedScrapDate, " +
            "       d.actual_scrap_date AS actualScrapDate, " +
            "       d.online_status AS onlineStatus, d.enable_status AS enableStatus, " +
            "       d.qr_code_url AS qrCodeUrl, d.certificate_url AS certificateUrl, " +
            "       d.last_online_time AS lastOnlineTime, d.create_time AS createTime " +
            "FROM asset_device d " +
            "LEFT JOIN asset_device_type t ON t.id = d.type_id AND t.is_deleted = 0 " +
            "LEFT JOIN asset_device_location l ON l.id = d.location_id AND l.is_deleted = 0 " +
            "<where>" +
            "  d.is_deleted = 0 " +
            "  <if test='deviceCode != null'> AND d.device_code = #{deviceCode} </if>" +
            "  <if test='deviceName != null'> AND d.device_name LIKE CONCAT('%', #{deviceName}, '%') </if>" +
            "  <if test='brand != null'> AND d.brand LIKE CONCAT('%', #{brand}, '%') </if>" +
            "  <if test='onlineStatus != null'> AND d.online_status = #{onlineStatus} </if>" +
            "  <if test='enableStatus != null'> AND d.enable_status = #{enableStatus} </if>" +
            "  <if test='typeIds != null and typeIds.size() > 0'>" +
            "    AND d.type_id IN " +
            "    <foreach collection='typeIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach>" +
            "  </if>" +
            "  <if test='locationIds != null and locationIds.size() > 0'>" +
            "    AND d.location_id IN " +
            "    <foreach collection='locationIds' item='lid' open='(' separator=',' close=')'>#{lid}</foreach>" +
            "  </if>" +
            "</where>" +
            "ORDER BY d.id DESC" +
            "</script>")
    IPage<DeviceVO> selectDevicePage(IPage<DeviceVO> page,
                                     @Param("deviceCode") String deviceCode,
                                     @Param("deviceName") String deviceName,
                                     @Param("typeIds") List<Long> typeIds,
                                     @Param("locationIds") List<Long> locationIds,
                                     @Param("onlineStatus") Integer onlineStatus,
                                     @Param("enableStatus") Integer enableStatus,
                                     @Param("brand") String brand);

    /**
     * 批量查询指定设备的监测点列表。
     *
     * @param deviceIds 设备ID集合
     */
    @Select("<script>" +
            "SELECT id, sensor_name AS sensorName, parent_id AS parentId, monitor_type AS monitorType, " +
            "       unit, warning_upper AS warningUpper, warning_lower AS warningLower, " +
            "       alarm_upper AS alarmUpper, alarm_lower AS alarmLower, " +
            "       spray_threshold AS sprayThreshold, refresh_rate AS refreshRate, status " +
            "FROM asset_device_sensor " +
            "WHERE is_deleted = 0 " +
            "AND parent_id IN " +
            "<foreach collection='deviceIds' item='did' open='(' separator=',' close=')'>#{did}</foreach>" +
            "ORDER BY id ASC" +
            "</script>")
    List<DeviceSensorVO> selectSensorsByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    /**
     * 查询指定父类型下的所有直接子类型ID。
     *
     * @param parentId 父类型ID
     */
    @Select("SELECT id FROM asset_device_type WHERE parent_id = #{parentId} AND is_deleted = 0")
    List<Long> selectChildTypeIds(@Param("parentId") Long parentId);

    /**
     * 查询指定父位置下的所有直接子位置ID。
     *
     * @param parentId 父位置ID
     */
    @Select("SELECT id FROM asset_device_location WHERE parent_id = #{parentId} AND is_deleted = 0")
    List<Long> selectChildLocationIds(@Param("parentId") Long parentId);

    /**
     * 查询指定设备在给定时间范围内重叠的离线日志（含仍未恢复的离线记录）。
     *
     * @param deviceId 设备ID
     * @param begin    统计开始时间
     * @param end      统计结束时间
     * @return 重叠的离线日志列表
     */
    @Select("SELECT offline_time AS offlineTime, online_time AS onlineTime " +
            "FROM asset_device_offline_log " +
            "WHERE device_id = #{deviceId} " +
            "  AND offline_time < #{end} " +
            "  AND (online_time IS NULL OR online_time > #{begin}) " +
            "ORDER BY offline_time ASC")
    List<DeviceOfflineLogVO> selectOfflineLogsForDevice(@Param("deviceId") Long deviceId,
                                                        @Param("begin") LocalDateTime begin,
                                                        @Param("end") LocalDateTime end);
}
