package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.BizHoistRealtime;
import com.qst.smartbuildings.vo.HoistRealtimeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 升降机实时监测数据 Mapper
 */
@Mapper
public interface BizHoistRealtimeMapper extends BaseMapper<BizHoistRealtime> {

    /**
     * 查询升降机最新实时数据（每台设备仅取最新一条）
     *
     * @param deviceId 设备编码（为空则返回所有设备）
     */
    @Select("<script>"
            + "SELECT t.id, t.device_id AS deviceId, a.device_name AS deviceName, t.collect_time AS collectTime, "
            + "       t.load_weight AS loadWeight, t.load_percent AS loadPercent, t.height AS height, "
            + "       t.run_direction AS runDirection, t.front_door_status AS frontDoorStatus, "
            + "       t.rear_door_status AS rearDoorStatus, t.passenger_count AS passengerCount, "
            + "       t.wind_speed AS windSpeed, t.interlock_alarm AS interlockAlarm "
            + "FROM biz_hoist_realtime t "
            + "INNER JOIN (SELECT device_id, MAX(collect_time) AS collect_time FROM biz_hoist_realtime "
            + "            <if test='deviceId != null and deviceId != \"\"'>WHERE device_id = #{deviceId}</if> "
            + "            GROUP BY device_id) m "
            + "  ON m.device_id = t.device_id AND m.collect_time = t.collect_time "
            + "LEFT JOIN asset_device a ON a.device_code = t.device_id AND a.is_deleted = 0 "
            + "ORDER BY t.device_id ASC"
            + "</script>")
    List<HoistRealtimeVO> selectLatest(@Param("deviceId") String deviceId);

    /**
     * 分页查询升降机历史数据，按采集时间倒序
     */
    @Select("<script>"
            + "SELECT t.id, t.device_id AS deviceId, t.collect_time AS collectTime, "
            + "       t.load_weight AS loadWeight, t.load_percent AS loadPercent, t.height AS height, "
            + "       t.run_direction AS runDirection, t.front_door_status AS frontDoorStatus, "
            + "       t.rear_door_status AS rearDoorStatus, t.passenger_count AS passengerCount, "
            + "       t.wind_speed AS windSpeed, t.interlock_alarm AS interlockAlarm "
            + "FROM biz_hoist_realtime t "
            + "WHERE t.device_id = #{deviceId} "
            + "<if test='startTime != null'> AND t.collect_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND t.collect_time &lt;= #{endTime} </if>"
            + "ORDER BY t.collect_time DESC"
            + "</script>")
    Page<HoistRealtimeVO> selectHistory(Page<?> page,
                                        @Param("deviceId") String deviceId,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 分页查询升降机联锁告警列表（interlock_alarm 非 0 的记录）
     */
    @Select("<script>"
            + "SELECT t.id, t.device_id AS deviceId, a.device_name AS deviceName, t.collect_time AS collectTime, "
            + "       t.load_weight AS loadWeight, t.load_percent AS loadPercent, t.height AS height, "
            + "       t.run_direction AS runDirection, t.front_door_status AS frontDoorStatus, "
            + "       t.rear_door_status AS rearDoorStatus, t.passenger_count AS passengerCount, "
            + "       t.wind_speed AS windSpeed, t.interlock_alarm AS interlockAlarm "
            + "FROM biz_hoist_realtime t "
            + "LEFT JOIN asset_device a ON a.device_code = t.device_id AND a.is_deleted = 0 "
            + "WHERE t.interlock_alarm IN (1, 2, 3, 4) "
            + "<if test='interlockAlarm != null'> AND t.interlock_alarm = #{interlockAlarm} </if>"
            + "<if test='deviceId != null and deviceId != \"\"'> AND t.device_id = #{deviceId} </if>"
            + "<if test='startTime != null'> AND t.collect_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND t.collect_time &lt;= #{endTime} </if>"
            + "ORDER BY t.collect_time DESC"
            + "</script>")
    Page<HoistRealtimeVO> selectInterlockAlarms(Page<?> page,
                                                @Param("interlockAlarm") Integer interlockAlarm,
                                                @Param("deviceId") String deviceId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);
}