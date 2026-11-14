package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.dto.HoistThresholdUpdateRequest;
import com.qst.smartbuildings.exception.HoistException;
import com.qst.smartbuildings.mapper.BizHoistRealtimeMapper;
import com.qst.smartbuildings.mapper.BizHoistThresholdMapper;
import com.qst.smartbuildings.mapper.BizHoistWorkRecordMapper;
import com.qst.smartbuildings.vo.HoistRealtimeVO;
import com.qst.smartbuildings.vo.HoistThresholdVO;
import com.qst.smartbuildings.vo.HoistWorkRecordVO;
import com.qst.smartbuildings.vo.HoistWorkStatisticsVO;
import com.qst.smartbuildings.vo.PageListVO;
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
 * 升降机监控服务
 */
@Service
public class HoistService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 支持的指标类型（与接口契约保持一致） */
    private static final Set<String> METRIC_TYPES = Set.of(
            "LOAD_WEIGHT", "LOAD_PERCENT", "WIND_SPEED", "HEIGHT", "PASSENGER_COUNT", "TILT_ANGLE");

    /** 指标类型 -> 指标类型文本（库表已移除 metric_type_text 列，由服务层推导） */
    private static final Map<String, String> METRIC_TYPE_TEXTS = Map.of(
            "LOAD_WEIGHT", "载重",
            "LOAD_PERCENT", "载重百分比",
            "WIND_SPEED", "风速",
            "HEIGHT", "高度",
            "PASSENGER_COUNT", "载人数",
            "TILT_ANGLE", "倾斜角");

    /** 指标类型 -> 默认单位（插入新阈值行时使用，unit 非空） */
    private static final Map<String, String> DEFAULT_UNITS = Map.of(
            "LOAD_WEIGHT", "kN",
            "LOAD_PERCENT", "%",
            "WIND_SPEED", "m/s",
            "HEIGHT", "m",
            "PASSENGER_COUNT", "人",
            "TILT_ANGLE", "°",
            "DOOR_INTERLOCK", "-");

    /** 布尔条件指标（离散事件，如双门同开） */
    private static final Set<String> BOOLEAN_METRIC_TYPES = Set.of("DOOR_INTERLOCK");

    private final BizHoistRealtimeMapper realtimeMapper;
    private final BizHoistWorkRecordMapper workRecordMapper;
    private final BizHoistThresholdMapper thresholdMapper;

    public HoistService(BizHoistRealtimeMapper realtimeMapper,
                        BizHoistWorkRecordMapper workRecordMapper,
                        BizHoistThresholdMapper thresholdMapper) {
        this.realtimeMapper = realtimeMapper;
        this.workRecordMapper = workRecordMapper;
        this.thresholdMapper = thresholdMapper;
    }

    /**
     * 查询升降机最新实时数据
     */
    public List<HoistRealtimeVO> latest(String deviceId) {
        List<HoistRealtimeVO> list = realtimeMapper.selectLatest(trimToNull(deviceId));
        list.forEach(this::fillStateTexts);
        return list;
    }

    /**
     * 分页查询升降机历史数据
     */
    public PageListVO<HoistRealtimeVO> history(String deviceId, String startTime, String endTime,
                                               long pageNum, long pageSize) {
        if (!StringUtils.hasText(deviceId)) {
            throw new HoistException("设备编码不能为空");
        }
        LocalDateTime begin = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        validateTimeRange(begin, end);
        Page<HoistRealtimeVO> page = realtimeMapper.selectHistory(
                new Page<>(pageNum, pageSize), deviceId.trim(), begin, end);
        page.getRecords().forEach(this::fillStateTexts);
        return PageListVO.of(page, page.getRecords());
    }

    /**
     * 分页查询升降机联锁告警列表
     */
    public PageListVO<HoistRealtimeVO> interlockAlarms(Integer interlockAlarm, String deviceId,
                                                       String startTime, String endTime,
                                                       long pageNum, long pageSize) {
        if (interlockAlarm != null && (interlockAlarm < 1 || interlockAlarm > 4)) {
            throw new HoistException("联锁告警类型仅支持 1-双门同开、2-超载、3-超速、4-超高");
        }
        LocalDateTime begin = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        validateTimeRange(begin, end);
        Page<HoistRealtimeVO> page = realtimeMapper.selectInterlockAlarms(
                new Page<>(pageNum, pageSize), interlockAlarm, trimToNull(deviceId), begin, end);
        page.getRecords().forEach(this::fillStateTexts);
        return PageListVO.of(page, page.getRecords());
    }

    /**
     * 查询升降机各指标阈值配置
     */
    public PageListVO<HoistThresholdVO> thresholds(String deviceId) {
        List<HoistThresholdVO> list = thresholdMapper.selectThresholds(trimToNull(deviceId));
        list.forEach(vo -> {
            String text = metricTypeText(vo.getMetricType());
            vo.setMetricTypeText(text);
            vo.setMetricLabel(text);
        });
        return PageListVO.of(list.size(), 1, list.size(), list);
    }

    /**
     * 修改升降机阈值配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateThresholds(HoistThresholdUpdateRequest request) {
        String metricType = request.getMetricType().trim().toUpperCase();
        if (!METRIC_TYPES.contains(metricType) && !BOOLEAN_METRIC_TYPES.contains(metricType)) {
            throw new HoistException("不支持的指标类型：" + request.getMetricType());
        }
        request.setMetricType(metricType);
        Long deviceId = thresholdMapper.selectDeviceIdByCode(request.getDeviceId().trim());
        if (deviceId == null) {
            throw new HoistException("升降机设备不存在或已删除：" + request.getDeviceId());
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
     * 分页查询升降机作业记录
     */
    public PageListVO<HoistWorkRecordVO> workRecords(String deviceId, String floorRange,
                                                     String startTime, String endTime,
                                                     long pageNum, long pageSize) {
        LocalDateTime begin = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        validateTimeRange(begin, end);
        Page<HoistWorkRecordVO> page = workRecordMapper.selectWorkRecords(
                new Page<>(pageNum, pageSize), trimToNull(deviceId), trimToNull(floorRange), begin, end);
        return PageListVO.of(page, page.getRecords());
    }

    /**
     * 升降机作业统计
     *
     * @param statDate 统计日期（yyyy-MM-dd），按作业开始时间所在日期统计
     */
    public HoistWorkStatisticsVO workStatistics(String deviceId, String statDate) {
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

    private void fillStateTexts(HoistRealtimeVO vo) {
        vo.setRunDirectionText(runDirectionText(vo.getRunDirection()));
        vo.setFrontDoorStatusText(doorStatusText(vo.getFrontDoorStatus()));
        vo.setRearDoorStatusText(doorStatusText(vo.getRearDoorStatus()));
        vo.setInterlockAlarmText(interlockAlarmText(vo.getInterlockAlarm()));
    }

    private String runDirectionText(Integer direction) {
        if (direction == null) {
            return null;
        }
        return switch (direction) {
            case 1 -> "上升";
            case 2 -> "下降";
            default -> "停止";
        };
    }

    private String doorStatusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "开";
            case 0 -> "关";
            default -> null;
        };
    }

    private String interlockAlarmText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "双门同开";
            case 2 -> "超载";
            case 3 -> "超速";
            case 4 -> "超高";
            default -> "正常";
        };
    }

    private String metricTypeText(String metricType) {
        if (metricType == null) {
            return null;
        }
        String upper = metricType.toUpperCase();
        return METRIC_TYPE_TEXTS.getOrDefault(upper, BOOLEAN_METRIC_TYPES.contains(upper) ? "双门同开联锁" : upper);
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
            throw new HoistException("时间参数格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new HoistException("日期参数格式不正确，应为 yyyy-MM-dd");
        }
    }

    private void validateTimeRange(LocalDateTime begin, LocalDateTime end) {
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new HoistException("开始时间不能晚于结束时间");
        }
    }
}