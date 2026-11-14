package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.dto.TowerCraneThresholdUpdateRequest;
import com.qst.smartbuildings.exception.TowerCraneException;
import com.qst.smartbuildings.mapper.BizTowerCraneRealtimeMapper;
import com.qst.smartbuildings.mapper.BizTowerCraneThresholdMapper;
import com.qst.smartbuildings.mapper.BizTowerCraneWorkRecordMapper;
import com.qst.smartbuildings.vo.PageListVO;
import com.qst.smartbuildings.vo.TowerCraneRealtimeVO;
import com.qst.smartbuildings.vo.TowerCraneThresholdVO;
import com.qst.smartbuildings.vo.TowerCraneWorkRecordVO;
import com.qst.smartbuildings.vo.TowerCraneWorkStatisticsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 塔吊监控服务
 */
@Service
public class TowerCraneService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 支持的指标类型（与接口契约保持一致） */
    private static final Set<String> METRIC_TYPES = Set.of(
            "LOAD_WEIGHT", "MOMENT", "MOMENT_PERCENT", "WIND_SPEED", "HEIGHT", "AMPLITUDE", "SLEW_ANGLE");

    /** 指标类型 -> 指标类型文本（库表已移除 metric_type_text 列，由服务层推导） */
    private static final Map<String, String> METRIC_TYPE_TEXTS = Map.of(
            "LOAD_WEIGHT", "起重量",
            "MOMENT", "力矩",
            "MOMENT_PERCENT", "力矩百分比",
            "WIND_SPEED", "风速",
            "HEIGHT", "高度",
            "AMPLITUDE", "幅度",
            "SLEW_ANGLE", "回转角度");

    /** 指标类型 -> 默认单位（插入新阈值行时使用，unit 非空） */
    private static final Map<String, String> DEFAULT_UNITS = Map.of(
            "LOAD_WEIGHT", "kN",
            "MOMENT", "kN·m",
            "MOMENT_PERCENT", "%",
            "WIND_SPEED", "m/s",
            "HEIGHT", "m",
            "AMPLITUDE", "m",
            "SLEW_ANGLE", "°");

    private final BizTowerCraneRealtimeMapper realtimeMapper;
    private final BizTowerCraneWorkRecordMapper workRecordMapper;
    private final BizTowerCraneThresholdMapper thresholdMapper;

    public TowerCraneService(BizTowerCraneRealtimeMapper realtimeMapper,
                             BizTowerCraneWorkRecordMapper workRecordMapper,
                             BizTowerCraneThresholdMapper thresholdMapper) {
        this.realtimeMapper = realtimeMapper;
        this.workRecordMapper = workRecordMapper;
        this.thresholdMapper = thresholdMapper;
    }

    /**
     * 查询塔吊最新实时数据
     */
    public List<TowerCraneRealtimeVO> latest(String deviceId) {
        List<TowerCraneRealtimeVO> list = realtimeMapper.selectLatest(trimToNull(deviceId));
        list.forEach(vo -> vo.setAlarmStatusText(alarmStatusText(vo.getAlarmStatus())));
        return list;
    }

    /**
     * 分页查询塔吊历史数据（时序曲线）
     */
    public PageListVO<TowerCraneRealtimeVO> history(String deviceId, String startTime, String endTime,
                                                    long pageNum, long pageSize) {
        if (!StringUtils.hasText(deviceId)) {
            throw new TowerCraneException("设备编码不能为空");
        }
        LocalDateTime begin = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        validateTimeRange(begin, end);
        Page<TowerCraneRealtimeVO> page = realtimeMapper.selectHistory(
                new Page<>(pageNum, pageSize), deviceId.trim(), begin, end);
        page.getRecords().forEach(vo -> vo.setAlarmStatusText(alarmStatusText(vo.getAlarmStatus())));
        return PageListVO.of(page, page.getRecords());
    }

    /**
     * 分页查询塔吊告警列表
     */
    public PageListVO<TowerCraneRealtimeVO> alarms(Integer alarmStatus, String deviceId,
                                                   String startTime, String endTime,
                                                   long pageNum, long pageSize) {
        if (alarmStatus != null && alarmStatus != 1 && alarmStatus != 2) {
            throw new TowerCraneException("告警状态仅支持 1-预警 或 2-报警");
        }
        LocalDateTime begin = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        validateTimeRange(begin, end);
        Page<TowerCraneRealtimeVO> page = realtimeMapper.selectAlarms(
                new Page<>(pageNum, pageSize), alarmStatus, trimToNull(deviceId), begin, end);
        page.getRecords().forEach(vo -> vo.setAlarmStatusText(alarmStatusText(vo.getAlarmStatus())));
        return PageListVO.of(page, page.getRecords());
    }

    /**
     * 查询塔吊各指标阈值配置
     */
    public PageListVO<TowerCraneThresholdVO> thresholds(String deviceId) {
        List<TowerCraneThresholdVO> list = thresholdMapper.selectThresholds(trimToNull(deviceId));
        list.forEach(vo -> {
            String text = metricTypeText(vo.getMetricType());
            vo.setMetricTypeText(text);
            vo.setMetricLabel(text);
        });
        return PageListVO.of(list.size(), 1, list.size(), list);
    }

    /**
     * 修改塔吊阈值配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateThresholds(TowerCraneThresholdUpdateRequest request) {
        String metricType = request.getMetricType().trim().toUpperCase();
        if (!METRIC_TYPES.contains(metricType)) {
            throw new TowerCraneException("不支持的指标类型：" + request.getMetricType());
        }
        request.setMetricType(metricType);
        Long deviceId = thresholdMapper.selectDeviceIdByCode(request.getDeviceId().trim());
        if (deviceId == null) {
            throw new TowerCraneException("塔吊设备不存在或已删除：" + request.getDeviceId());
        }
        String monitorType = metricType.toLowerCase();
        String sensorName = request.getDeviceId().trim() + "-" + metricTypeText(metricType);
        String unit = StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : DEFAULT_UNITS.get(metricType);
        Long sensorId = thresholdMapper.selectSensorIdByDeviceAndType(deviceId, monitorType);
        if (sensorId == null) {
            thresholdMapper.insertThreshold(sensorName, deviceId, monitorType, unit,
                    request.getThresholdWarnUpper(), request.getThresholdWarnLower(),
                    request.getThresholdAlarmUpper(), request.getThresholdAlarmLower());
        } else {
            thresholdMapper.updateThresholds(sensorId, unit,
                    request.getThresholdWarnUpper(), request.getThresholdWarnLower(),
                    request.getThresholdAlarmUpper(), request.getThresholdAlarmLower());
        }
    }

    /**
     * 分页查询塔吊作业记录
     */
    public PageListVO<TowerCraneWorkRecordVO> workRecords(String deviceId, Long locationId,
                                                          String startTime, String endTime,
                                                          long pageNum, long pageSize) {
        LocalDateTime begin = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        validateTimeRange(begin, end);
        Page<TowerCraneWorkRecordVO> page = workRecordMapper.selectWorkRecords(
                new Page<>(pageNum, pageSize), trimToNull(deviceId), locationId, begin, end);
        return PageListVO.of(page, page.getRecords());
    }

    /**
     * 塔吊作业统计汇总
     *
     * @param statDate 统计日期（yyyy-MM-dd），按作业开始时间所在日期统计
     */
    public TowerCraneWorkStatisticsVO workStatistics(String deviceId, String statDate) {
        LocalDateTime begin = null;
        LocalDateTime end = null;
        if (StringUtils.hasText(statDate)) {
            LocalDate date = parseDate(statDate);
            begin = date.atStartOfDay();
            end = date.atTime(LocalTime.of(23, 59, 59));
        }
        return workRecordMapper.selectWorkStatistics(trimToNull(deviceId), begin, end);
    }

    // ==================== 私有方法 ====================

    private String alarmStatusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "预警";
            case 2 -> "报警";
            default -> "正常";
        };
    }

    private String metricTypeText(String metricType) {
        return metricType == null ? null : METRIC_TYPE_TEXTS.get(metricType.toUpperCase());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new TowerCraneException("时间参数格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new TowerCraneException("日期参数格式不正确，应为 yyyy-MM-dd");
        }
    }

    private void validateTimeRange(LocalDateTime begin, LocalDateTime end) {
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new TowerCraneException("开始时间不能晚于结束时间");
        }
    }
}