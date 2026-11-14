package com.qst.smartbuildings.service;

import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.AssetDeviceType;
import com.qst.smartbuildings.domain.BizHoistRealtime;
import com.qst.smartbuildings.domain.BizTowerCraneRealtime;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.domain.EnvMonitorData;
import com.qst.smartbuildings.mapper.AssetDeviceMapper;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.AssetDeviceTypeMapper;
import com.qst.smartbuildings.mapper.BizHoistRealtimeMapper;
import com.qst.smartbuildings.mapper.BizTowerCraneRealtimeMapper;
import com.qst.smartbuildings.mapper.EnvMonitorDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 传感器实时数据持久化：将 SensorTcpServer 收到的读数写入传感器 parentId 对应的实时监测数据表。
 * <p>
 * 路由规则（与数据库表设计 V1.4 一致）：
 * <ul>
 *   <li>parent_type=2（环境监测点）：写入 biz_env_monitor_data，parent_id=传感器 parentId，status 由阈值判定；</li>
 *   <li>parent_type=1（设备）：按设备类型写入 biz_tower_crane_realtime（塔吊，alarm_status）或
 *       biz_hoist_realtime（升降机，interlock_alarm）；</li>
 * <li>parent_type=0（无父级）：不落库，仅记日志。</li>
 * </ul>
 * 告警状态沿用环境监测的阈值判定：报警=2，预警=1，正常=0。
 * <p>
 * 达到告警阈值（status=1/2）时，同步将告警信息写入告警记录主表 biz_alarm_record
 * （环境=source_type 2，塔吊/升降机=source_type 1）；恢复正常时自动恢复告警。
 * 缺省项目取自配置项 {@code app.sensor-alarm-project-id}（默认 1）。
 */
@Service
public class SensorDataPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(SensorDataPersistenceService.class);

    private static final DateTimeFormatter COLLECT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");

    private final AssetDeviceSensorMapper sensorMapper;
    private final AssetDeviceMapper deviceMapper;
    private final AssetDeviceTypeMapper deviceTypeMapper;
    private final EnvMonitorDataMapper envDataMapper;
    private final BizTowerCraneRealtimeMapper towerRealtimeMapper;
    private final BizHoistRealtimeMapper hoistRealtimeMapper;
    private final AlarmService alarmService;
    private final Long alarmProjectId;

    public SensorDataPersistenceService(AssetDeviceSensorMapper sensorMapper,
                                        AssetDeviceMapper deviceMapper,
                                        AssetDeviceTypeMapper deviceTypeMapper,
                                        EnvMonitorDataMapper envDataMapper,
                                        BizTowerCraneRealtimeMapper towerRealtimeMapper,
                                        BizHoistRealtimeMapper hoistRealtimeMapper,
                                        AlarmService alarmService,
                                        @Value("${app.sensor-alarm-project-id:1}") Long alarmProjectId) {
        this.sensorMapper = sensorMapper;
        this.deviceMapper = deviceMapper;
        this.deviceTypeMapper = deviceTypeMapper;
        this.envDataMapper = envDataMapper;
        this.towerRealtimeMapper = towerRealtimeMapper;
        this.hoistRealtimeMapper = hoistRealtimeMapper;
        this.alarmService = alarmService;
        this.alarmProjectId = alarmProjectId;
    }

    /**
     * 将一条传感器读数写入其父级对应的实时监测数据表。
     *
     * @param sensorId   传感器 id（TCP 上报键）
     * @param metricType 指标类型（如 load_weight / wind_speed / PM25）
     * @param metricValue 监测值
     * @param unit       单位（可为空）
     * @param collectTime 采集时间（yyyy-MM-dd HH:mm:ss[.SSS]）
     */
    public void persist(Long sensorId, String metricType, BigDecimal metricValue,
                        String unit, String collectTime) {
        if (sensorId == null || metricValue == null) {
            return;
        }
        AssetDeviceSensor sensor = sensorMapper.selectById(sensorId);
        if (sensor == null || sensor.getIsDeleted() != null && sensor.getIsDeleted() == 1) {
            log.warn("传感器 {} 不存在或已删除，跳过持久化", sensorId);
            return;
        }
        LocalDateTime collect = parseCollectTime(collectTime);
        int status = determineStatus(sensor, metricValue);
        Integer parentType = sensor.getParentType();
        if (parentType != null && parentType == 2) {
            persistEnv(sensor, metricType, metricValue, unit, collect, status);
        } else if (parentType != null && parentType == 1) {
            persistDevice(sensor, metricType, metricValue, unit, collect, status);
        } else {
            log.debug("传感器 {} 无父级（parent_type={}），不写入实时数据表", sensorId, parentType);
        }
    }

    // ==================== 环境监测 ====================

    private void persistEnv(AssetDeviceSensor sensor, String metricType, BigDecimal metricValue,
                            String unit, LocalDateTime collectTime, int status) {
        EnvMonitorData data = new EnvMonitorData();
        data.setMonitorPointId(sensor.getParentId());
        data.setCollectTime(collectTime);
        data.setMetricType(StringUtils.hasText(metricType) ? metricType.trim() : sensor.getMonitorType());
        data.setMetricValue(metricValue);
        data.setUnit(StringUtils.hasText(unit) ? unit.trim() : sensor.getUnit());
        data.setStatus(status);
        data.setCreatedAt(LocalDateTime.now());
        try {
            envDataMapper.insert(data);
        } catch (DuplicateKeyException ex) {
            log.debug("环境监测数据已存在，跳过: parentId={}, metric={}, collectTime={}",
                    sensor.getParentId(), data.getMetricType(), collectTime);
        }
        triggerAlarm(2, null, sensor.getId(), sensor, data.getMetricType(), metricValue, status,
                collectTime, null);
    }

    // ==================== 塔吊 / 升降机 ====================

    private void persistDevice(AssetDeviceSensor sensor, String metricType, BigDecimal metricValue,
                               String unit, LocalDateTime collectTime, int status) {
        Device device = deviceMapper.selectById(sensor.getParentId());
        if (device == null || device.getIsDeleted() != null && device.getIsDeleted() == 1) {
            log.warn("传感器 {} 的设备父级 {} 不存在，跳过持久化", sensor.getId(), sensor.getParentId());
            return;
        }
        String kind = resolveDeviceKind(device.getTypeId());
        if (kind == null) {
            log.debug("传感器 {} 的设备 {} 类型未知，跳过持久化", sensor.getId(), device.getDeviceCode());
            return;
        }
        if ("tower".equals(kind)) {
            persistTower(sensor, device, metricType, metricValue, collectTime, status);
        } else {
            persistHoist(sensor, device, metricType, metricValue, collectTime, status);
        }
    }

    /** 按设备类型名/编码识别塔吊与升降机；无法识别返回 null。 */
    private String resolveDeviceKind(Long typeId) {
        if (typeId == null) {
            return null;
        }
        AssetDeviceType type = deviceTypeMapper.selectById(typeId);
        if (type == null) {
            return null;
        }
        String name = type.getTypeName() == null ? "" : type.getTypeName();
        String code = type.getTypeCode() == null ? "" : type.getTypeCode().toUpperCase(Locale.ROOT);
        if (name.contains("塔吊") || code.contains("CRANE") || code.contains("TD")) {
            return "tower";
        }
        if (name.contains("升降机") || code.contains("ELEVATOR") || code.contains("HOIST") || code.contains("SJJ")) {
            return "hoist";
        }
        return null;
    }

    private void persistTower(AssetDeviceSensor sensor, Device device, String metricType, BigDecimal metricValue,
                              LocalDateTime collectTime, int status) {
        BizTowerCraneRealtime row = new BizTowerCraneRealtime();
        row.setDeviceId(device.getDeviceCode());
        row.setCollectTime(collectTime);
        row.setAlarmStatus(status);
        row.setCreatedAt(LocalDateTime.now());
        String metric = normalizeMetric(metricType);
        switch (metric) {
            case "load_weight", "load" -> row.setLoadWeight(metricValue);
            case "amplitude" -> row.setAmplitude(metricValue);
            case "moment" -> row.setMoment(metricValue);
            case "moment_percent" -> row.setMomentPercent(metricValue);
            case "wind_speed", "wind" -> row.setWindSpeed(metricValue);
            case "height" -> row.setHeight(metricValue);
            case "slew_angle", "rotation" -> row.setSlewAngle(metricValue);
            default -> {
                log.debug("塔吊传感器指标 {} 不支持，跳过持久化", metricType);
                return;
            }
        }
        towerRealtimeMapper.insert(row);
        triggerAlarm(1, device.getId(), sensor.getId(), sensor, metricType, metricValue, status,
                collectTime, device.getDeviceName());
    }

    private void persistHoist(AssetDeviceSensor sensor, Device device, String metricType, BigDecimal metricValue,
                              LocalDateTime collectTime, int status) {
        BizHoistRealtime row = new BizHoistRealtime();
        row.setDeviceId(device.getDeviceCode());
        row.setCollectTime(collectTime);
        row.setInterlockAlarm(interlockAlarm(metricType, status));
        row.setCreatedAt(LocalDateTime.now());
        String metric = normalizeMetric(metricType);
        switch (metric) {
            case "load_weight", "load" -> row.setLoadWeight(metricValue);
            case "load_percent" -> row.setLoadPercent(metricValue);
            case "height" -> row.setHeight(metricValue);
            case "wind_speed", "wind" -> row.setWindSpeed(metricValue);
            case "passenger_count", "passengers" -> row.setPassengerCount(metricValue.intValue());
            case "run_direction" -> row.setRunDirection(metricValue.intValue());
            case "front_door_status", "front_door" -> row.setFrontDoorStatus(metricValue.intValue());
            case "rear_door_status", "rear_door" -> row.setRearDoorStatus(metricValue.intValue());
            default -> {
                log.debug("升降机传感器指标 {} 不支持，跳过持久化", metricType);
                return;
            }
        }
        hoistRealtimeMapper.insert(row);
        triggerAlarm(1, device.getId(), sensor.getId(), sensor, metricType, metricValue, status,
                collectTime, device.getDeviceName());
    }

    /** 升降机联锁告警映射：0-正常，1-双门同开，2-超载，3-超速，4-超高。 */
    private int interlockAlarm(String metricType, int status) {
        if (status <= 0) {
            return 0;
        }
        return switch (normalizeMetric(metricType)) {
            case "door_interlock", "interlock" -> 1;
            case "wind_speed", "wind" -> 3;
            case "height" -> 4;
            default -> 2;
        };
    }

    // ==================== 工具 ====================

    /**
     * 达到阈值（status>0）时将告警写入 biz_alarm_record；恢复正常（status=0）时自动恢复。
     * monitorPointId 使用传感器自身 id（环境使用其监测点 parentId 以对齐既有环境告警查询）。
     */
    private void triggerAlarm(Integer sourceType, Long deviceId, Long monitorPointId, AssetDeviceSensor sensor,
                              String metricType, BigDecimal metricValue, int status, LocalDateTime collectTime,
                              String deviceName) {
        if (alarmService == null || alarmProjectId == null || alarmProjectId < 1) {
            return;
        }
        try {
            String thresholdRange = "预警上限=" + sensor.getWarningUpper() + ", 报警上限=" + sensor.getAlarmUpper();
            String content = (deviceName != null ? deviceName + "：" : "")
                    + (StringUtils.hasText(metricType) ? metricType : sensor.getMonitorType())
                    + "监测值" + metricValue + "超出阈值";
            if (sourceType != null && sourceType == 2) {
                alarmService.processEnvironmentAlarm(alarmProjectId, deviceId, monitorPointId, metricType,
                        metricValue, status, collectTime, thresholdRange);
            } else {
                alarmService.processDeviceAlarm(alarmProjectId, deviceId, monitorPointId, metricType,
                        metricValue, status, collectTime, thresholdRange, content);
            }
        } catch (Exception ex) {
            log.warn("写入告警记录失败: sensorId={}, metric={}, err={}", sensor.getId(), metricType, ex.getMessage());
        }
    }

    /** 阈值判定：报警=2，预警=1，正常=0（与环境监测一致）。 */
    private int determineStatus(AssetDeviceSensor sensor, BigDecimal value) {
        if (sensor.getAlarmUpper() != null && value.compareTo(sensor.getAlarmUpper()) >= 0) return 2;
        if (sensor.getAlarmLower() != null && value.compareTo(sensor.getAlarmLower()) <= 0) return 2;
        if (sensor.getWarningUpper() != null && value.compareTo(sensor.getWarningUpper()) >= 0) return 1;
        if (sensor.getWarningLower() != null && value.compareTo(sensor.getWarningLower()) <= 0) return 1;
        return 0;
    }

    private String normalizeMetric(String metricType) {
        return metricType == null ? "" : metricType.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime parseCollectTime(String collectTime) {
        if (!StringUtils.hasText(collectTime)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(collectTime.trim(), COLLECT_TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            log.warn("采集时间格式无法解析（{}），使用当前时间代替", collectTime);
            return LocalDateTime.now();
        }
    }
}