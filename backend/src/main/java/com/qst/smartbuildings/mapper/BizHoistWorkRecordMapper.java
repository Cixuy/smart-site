package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.BizHoistWorkRecord;
import com.qst.smartbuildings.vo.HoistWorkRecordVO;
import com.qst.smartbuildings.vo.HoistWorkStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 升降机作业记录 Mapper
 */
@Mapper
public interface BizHoistWorkRecordMapper extends BaseMapper<BizHoistWorkRecord> {

    /**
     * 分页查询升降机作业记录（可带设备、楼层区间、时间范围筛选）
     */
    @Select("<script>"
            + "SELECT w.id, w.device_id AS deviceId, a.device_name AS deviceName, "
            + "       w.start_time AS startTime, w.end_time AS endTime, w.avg_load AS avgLoad, "
            + "       w.max_load AS maxLoad, w.max_passengers AS maxPassengers, w.floor_range AS floorRange, "
            + "       w.avg_wind_speed AS avgWindSpeed, w.max_tilt_angle AS maxTiltAngle, "
            + "       w.height_range AS heightRange "
            + "FROM biz_hoist_work_record w "
            + "LEFT JOIN asset_device a ON a.device_code = w.device_id AND a.is_deleted = 0 "
            + "WHERE w.is_deleted = 0 "
            + "<if test='deviceId != null and deviceId != \"\"'> AND w.device_id = #{deviceId} </if>"
            + "<if test='floorRange != null and floorRange != \"\"'> AND w.floor_range LIKE CONCAT('%', #{floorRange}, '%') </if>"
            + "<if test='startTime != null'> AND w.start_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND w.end_time &lt;= #{endTime} </if>"
            + "ORDER BY w.start_time DESC"
            + "</script>")
    Page<HoistWorkRecordVO> selectWorkRecords(Page<?> page,
                                              @Param("deviceId") String deviceId,
                                              @Param("floorRange") String floorRange,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 升降机作业统计汇总（作业次数、总时长、平均/最大载重、作业期告警次数）
     */
    @Select("<script>"
            + "SELECT COUNT(*) AS totalWorkCount, "
            + "       ROUND(COALESCE(SUM(TIMESTAMPDIFF(MINUTE, w.start_time, w.end_time)), 0) / 60.0, 2) AS totalWorkHours, "
            + "       ROUND(COALESCE(AVG(w.avg_load), 0), 2) AS avgLoad, "
            + "       ROUND(COALESCE(MAX(w.max_load), 0), 2) AS maxLoad, "
            + "       COALESCE(SUM(CASE WHEN EXISTS ("
            + "           SELECT 1 FROM biz_hoist_realtime r "
            + "           WHERE r.device_id = w.device_id "
            + "             AND r.collect_time BETWEEN w.start_time AND w.end_time "
            + "             AND r.interlock_alarm IN (1, 2, 3, 4)"
            + "       ) THEN 1 ELSE 0 END), 0) AS alarmCount "
            + "FROM biz_hoist_work_record w "
            + "WHERE w.is_deleted = 0 "
            + "<if test='deviceId != null and deviceId != \"\"'> AND w.device_id = #{deviceId} </if>"
            + "<if test='startTime != null'> AND w.start_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND w.end_time &lt;= #{endTime} </if>"
            + "</script>")
    HoistWorkStatisticsVO selectWorkStatistics(@Param("deviceId") String deviceId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);
}