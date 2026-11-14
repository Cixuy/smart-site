package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.BizTowerCraneRealtime;
import com.qst.smartbuildings.vo.TowerCraneRealtimeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 塔吊实时监测数据 Mapper
 */
@Mapper
public interface BizTowerCraneRealtimeMapper extends BaseMapper<BizTowerCraneRealtime> {

    /**
     * 查询塔吊最新实时数据（每台设备仅取最新一条）
     *
     * @param deviceId 设备编码（为空则返回所有设备）
     */
    @Select("<script>"
            + "SELECT t.id, t.device_id AS deviceId, a.device_name AS deviceName, "
            + "       t.collect_time AS collectTime, t.load_weight AS loadWeight, t.amplitude AS amplitude, "
            + "       t.moment AS moment, t.moment_percent AS momentPercent, t.wind_speed AS windSpeed, "
            + "       t.height AS height, t.slew_angle AS slewAngle, t.alarm_status AS alarmStatus "
            + "FROM biz_tower_crane_realtime t "
            + "INNER JOIN (SELECT device_id, MAX(collect_time) AS collect_time FROM biz_tower_crane_realtime "
            + "            <if test='deviceId != null and deviceId != \"\"'>WHERE device_id = #{deviceId}</if> "
            + "            GROUP BY device_id) m "
            + "  ON m.device_id = t.device_id AND m.collect_time = t.collect_time "
            + "LEFT JOIN asset_device a ON a.device_code = t.device_id AND a.is_deleted = 0 "
            + "ORDER BY t.device_id ASC"
            + "</script>")
    List<TowerCraneRealtimeVO> selectLatest(@Param("deviceId") String deviceId);

    /**
     * 分页查询塔吊历史数据（时序曲线），按采集时间倒序
     */
    @Select("<script>"
            + "SELECT t.id, t.device_id AS deviceId, t.collect_time AS collectTime, "
            + "       t.load_weight AS loadWeight, t.amplitude AS amplitude, t.moment AS moment, "
            + "       t.moment_percent AS momentPercent, t.wind_speed AS windSpeed, t.height AS height, "
            + "       t.slew_angle AS slewAngle, t.alarm_status AS alarmStatus "
            + "FROM biz_tower_crane_realtime t "
            + "WHERE t.device_id = #{deviceId} "
            + "<if test='startTime != null'> AND t.collect_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND t.collect_time &lt;= #{endTime} </if>"
            + "ORDER BY t.collect_time DESC"
            + "</script>")
    Page<TowerCraneRealtimeVO> selectHistory(Page<?> page,
                                             @Param("deviceId") String deviceId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 分页查询塔吊告警列表（alarm_status 为预警/报警的记录）
     */
    @Select("<script>"
            + "SELECT t.id, t.device_id AS deviceId, a.device_name AS deviceName, t.collect_time AS collectTime, "
            + "       t.load_weight AS loadWeight, t.amplitude AS amplitude, t.moment AS moment, "
            + "       t.moment_percent AS momentPercent, t.wind_speed AS windSpeed, t.height AS height, "
            + "       t.slew_angle AS slewAngle, t.alarm_status AS alarmStatus "
            + "FROM biz_tower_crane_realtime t "
            + "LEFT JOIN asset_device a ON a.device_code = t.device_id AND a.is_deleted = 0 "
            + "WHERE t.alarm_status IN (1, 2) "
            + "<if test='alarmStatus != null'> AND t.alarm_status = #{alarmStatus} </if>"
            + "<if test='deviceId != null and deviceId != \"\"'> AND t.device_id = #{deviceId} </if>"
            + "<if test='startTime != null'> AND t.collect_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND t.collect_time &lt;= #{endTime} </if>"
            + "ORDER BY t.collect_time DESC"
            + "</script>")
    Page<TowerCraneRealtimeVO> selectAlarms(Page<?> page,
                                            @Param("alarmStatus") Integer alarmStatus,
                                            @Param("deviceId") String deviceId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
}