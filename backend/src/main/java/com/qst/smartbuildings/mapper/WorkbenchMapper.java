package com.qst.smartbuildings.mapper;

import com.qst.smartbuildings.vo.workbench.AlarmSummary;
import com.qst.smartbuildings.vo.workbench.TodoAlarm;
import com.qst.smartbuildings.vo.workbench.TrendPoint;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台数据访问层
 */
@Mapper
public interface WorkbenchMapper {

    /**
     * 在线设备数（启用且在线）
     */
    @Select("SELECT COUNT(*) FROM asset_device WHERE is_deleted = 0 AND enable_status = 1 AND online_status = 1")
    long countOnlineDevices();

    /**
     * 离线设备数（启用且离线）
     */
    @Select("SELECT COUNT(*) FROM asset_device WHERE is_deleted = 0 AND enable_status = 1 AND online_status = 0")
    long countOfflineDevices();

    /**
     * 当日告警总数（按项目 + 时间范围）
     */
    @Select("SELECT COUNT(*) FROM biz_alarm_record WHERE project_id = #{projectId} AND is_deleted = 0 " +
            "AND alarm_time >= #{start} AND alarm_time < #{end}")
    long countDailyAlarms(@Param("projectId") Long projectId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    /**
     * 未处理告警数（按项目）
     */
    @Select("SELECT COUNT(*) FROM biz_alarm_record WHERE project_id = #{projectId} AND status = 0 AND is_deleted = 0")
    long countPendingAlarms(@Param("projectId") Long projectId);

    /**
     * 告警总数（按项目，剔除已删除）
     */
    @Select("SELECT COUNT(*) FROM biz_alarm_record WHERE project_id = #{projectId} AND is_deleted = 0")
    long countTotalAlarms(@Param("projectId") Long projectId);

    /**
     * 已处置告警数（按项目，status=2）
     */
    @Select("SELECT COUNT(*) FROM biz_alarm_record WHERE project_id = #{projectId} AND status = 2 AND is_deleted = 0")
    long countDisposedAlarms(@Param("projectId") Long projectId);

    /**
     * 最近N条未处置告警（级别高优先，再按时间倒序）
     *
     * @param projectId 项目ID
     * @param limit     返回条数
     */
    @Select("SELECT r.id AS alarmId, r.alarm_type_code AS alarmType, " +
            "CASE r.alarm_level WHEN 1 THEN 'warning' WHEN 2 THEN 'alarm' ELSE 'control' END AS alarmLevel, " +
            "r.device_id AS deviceId, d.device_name AS deviceName, r.alarm_time AS alarmTime " +
            "FROM biz_alarm_record r LEFT JOIN asset_device d ON r.device_id = d.id " +
            "WHERE r.project_id = #{projectId} AND r.status = 0 AND r.is_deleted = 0 " +
            "ORDER BY FIELD(r.alarm_level, 3, 2, 1), r.alarm_time DESC LIMIT #{limit}")
    List<AlarmSummary> selectLatestAlarms(@Param("projectId") Long projectId,
                                          @Param("limit") int limit);

    /**
     * 最新未处理告警滚动列表（严格按时间倒序）
     *
     * @param projectId 项目ID
     * @param limit     返回条数
     */
    @Select("SELECT r.id AS alarmId, r.alarm_type_code AS alarmType, " +
            "CASE r.alarm_level WHEN 1 THEN 'warning' WHEN 2 THEN 'alarm' ELSE 'control' END AS alarmLevel, " +
            "r.device_id AS deviceId, d.device_name AS deviceName, r.alarm_time AS alarmTime " +
            "FROM biz_alarm_record r LEFT JOIN asset_device d ON r.device_id = d.id " +
            "WHERE r.project_id = #{projectId} AND r.status = 0 AND r.is_deleted = 0 " +
            "ORDER BY r.alarm_time DESC LIMIT #{limit}")
    List<AlarmSummary> selectLatestAlarmsByProjectId(@Param("projectId") Long projectId,
                                                     @Param("limit") int limit);

    /**
     * 待办告警列表（未处置 + 处置中）
     */
    @Select("SELECT r.id AS alarmId, r.alarm_type_code AS alarmType, " +
            "CASE r.alarm_level WHEN 1 THEN 'warning' WHEN 2 THEN 'alarm' ELSE 'control' END AS alarmLevel, " +
            "r.device_id AS deviceId, d.device_name AS deviceName, r.alarm_time AS alarmTime, " +
            "CASE r.status WHEN 0 THEN 'pending' ELSE 'processing' END AS status, " +
            "CONCAT('/alarms/', r.id) AS disposalUrl " +
            "FROM biz_alarm_record r LEFT JOIN asset_device d ON r.device_id = d.id " +
            "WHERE r.project_id = #{projectId} AND r.status IN (0, 1) AND r.is_deleted = 0 " +
            "ORDER BY FIELD(r.alarm_level, 3, 2, 1), r.alarm_time DESC LIMIT 20")
    List<TodoAlarm> selectTodoAlarms(@Param("projectId") Long projectId);

    /**
     * 待办告警列表（支持指定返回条数）
     *
     * @param projectId 项目ID
     * @param limit     返回条数
     */
    @Select("SELECT r.id AS alarmId, r.alarm_type_code AS alarmType, " +
            "CASE r.alarm_level WHEN 1 THEN 'warning' WHEN 2 THEN 'alarm' ELSE 'control' END AS alarmLevel, " +
            "r.device_id AS deviceId, d.device_name AS deviceName, r.alarm_time AS alarmTime, " +
            "CASE r.status WHEN 0 THEN 'pending' ELSE 'processing' END AS status, " +
            "CONCAT('/alarms/', r.id) AS disposalUrl " +
            "FROM biz_alarm_record r LEFT JOIN asset_device d ON r.device_id = d.id " +
            "WHERE r.project_id = #{projectId} AND r.status IN (0, 1) AND r.is_deleted = 0 " +
            "ORDER BY FIELD(r.alarm_level, 3, 2, 1), r.alarm_time DESC LIMIT #{limit}")
    List<TodoAlarm> selectTodoAlarmsByProjectId(@Param("projectId") Long projectId,
                                                @Param("limit") int limit);


    /**
     * 近N日告警趋势（按日聚合预统计表）
     */
    @Select("SELECT stat_date AS date, SUM(total_count) AS value FROM biz_alarm_daily_stat " +
            "WHERE project_id = #{projectId} AND stat_date >= #{startDate} " +
            "GROUP BY stat_date ORDER BY stat_date")
    List<TrendPoint> selectAlarmTrend(@Param("projectId") Long projectId,
                                      @Param("startDate") LocalDate startDate);

    /**
     * 今日喷淋操作记录数（自动/手动/定时启动动作计数）
     */
    @Select("SELECT COUNT(*) FROM spray_operation_log WHERE operation_type = 'start' " +
            "AND create_time >= #{start} AND create_time < #{end}")
    long countTodaySprayRecords(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /**
     * 全局搜索 - 按关键字搜索设备
     *
     * @param keyword 搜索关键字
     * @param limit   返回条数
     */
    @Select("SELECT d.id AS id, d.device_name AS title, d.device_code AS deviceCode, " +
            "CASE WHEN d.online_status = 1 AND d.enable_status = 1 THEN '当前在线' " +
            "WHEN d.online_status = 0 AND d.enable_status = 1 THEN '当前离线' ELSE '已禁用' END AS deviceStatus " +
            "FROM asset_device d " +
            "WHERE d.is_deleted = 0 AND (d.device_name LIKE CONCAT('%', #{keyword}, '%') OR d.device_code LIKE CONCAT('%', #{keyword}, '%')) " +
            "LIMIT #{limit}")
    List<DeviceSearchRow> searchDevices(@Param("keyword") String keyword,
                                        @Param("limit") int limit);

    /**
     * 全局搜索 - 按关键字搜索告警
     *
     * @param keyword 搜索关键字
     * @param limit   返回条数
     */
    @Select("SELECT r.id AS id, r.alarm_type_code AS title, " +
            "CASE r.alarm_level WHEN 1 THEN 'warning' WHEN 2 THEN 'alarm' ELSE 'control' END AS alarmLevel, " +
            "r.alarm_time AS alarmTime, d.device_name AS deviceName " +
            "FROM biz_alarm_record r LEFT JOIN asset_device d ON r.device_id = d.id " +
            "WHERE r.is_deleted = 0 AND r.alarm_type_code LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT #{limit}")
    List<AlarmSearchRow> searchAlarms(@Param("keyword") String keyword,
                                      @Param("limit") int limit);

    /**
     * 全局搜索 - 按关键字搜索用户
     *
     * @param keyword 搜索关键字
     * @param limit   返回条数
     */
    @Select("SELECT id, username, real_name AS realName, phone, email " +
            "FROM sys_user WHERE is_deleted = 0 AND (username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR real_name LIKE CONCAT('%', #{keyword}, '%')) " +
            "LIMIT #{limit}")
    List<UserSearchRow> searchUsers(@Param("keyword") String keyword,
                                    @Param("limit") int limit);

    /**
     * 设备搜索行
     */
    @Data
    class DeviceSearchRow {
        private Long id;
        private String title;
        private String deviceCode;
        private String deviceStatus;
    }

    /**
     * 告警搜索行
     */
    @Data
    class AlarmSearchRow {
        private Long id;
        private String title;
        private String alarmLevel;
        private LocalDateTime alarmTime;
        private String deviceName;
    }

    /**
     * 用户搜索行
     */
    @Data
    class UserSearchRow {
        private Long id;
        private String username;
        private String realName;
        private String phone;
        private String email;
    }
}
