package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.EnvMonitorData;
import com.qst.smartbuildings.dto.EnvDataBatchReportRequest;
import com.qst.smartbuildings.dto.EnvDataManualRequest;
import com.qst.smartbuildings.dto.EnvDataReportRequest;
import com.qst.smartbuildings.dto.EnvThresholdUpdateRequest;
import com.qst.smartbuildings.dto.SensorDataPushRequest;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.exception.EnvMonitorPointNotFoundException;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.EnvMonitorDataMapper;
import com.qst.smartbuildings.vo.EnvBatchPushResult;
import com.qst.smartbuildings.vo.EnvDailyStatVO;
import com.qst.smartbuildings.vo.EnvDataPushResult;
import com.qst.smartbuildings.vo.EnvMonitorRecordVO;
import com.qst.smartbuildings.vo.EnvThresholdVO;
import com.qst.smartbuildings.vo.EnvTrendVO;
import com.qst.smartbuildings.vo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Environment data ingestion, threshold evaluation and query operations. */
@Service
public class EnvMonitorService {
    private static final List<String> METRIC_TYPES = List.of("PM25", "PM10", "NOISE", "TEMP", "HUMIDITY", "WIND");
    private static final Map<String, String> METRIC_TEXT = Map.of(
            "PM25", "PM2.5", "PM10", "PM10", "NOISE", "噪声", "TEMP", "温度",
            "HUMIDITY", "湿度", "WIND", "风速");

    private final EnvMonitorDataMapper dataMapper;
    private final AssetDeviceSensorMapper sensorMapper;
    private final AlarmService alarmService;

    public EnvMonitorService(EnvMonitorDataMapper dataMapper, AssetDeviceSensorMapper sensorMapper, AlarmService alarmService) {
        this.dataMapper = dataMapper;
        this.sensorMapper = sensorMapper;
        this.alarmService = alarmService;
    }

    /** Backwards-compatible endpoint used by the sensor management module. */
    @Transactional(rollbackFor = Exception.class)
    public EnvDataPushResult pushSensorData(SensorDataPushRequest request) {
        AssetDeviceSensor sensor = requireSensor(request == null ? null : request.getSensorId());
        if (!METRIC_TYPES.contains(sensor.getMonitorType())) {
            throw new MonitoringException("该监测点的指标类型不支持环境数据上报");
        }
        return save(sensor, sensor.getMonitorType(), sensor.getUnit(), request.getMetricValue(), request.getCollectTime(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public EnvDataPushResult report(EnvDataReportRequest request) {
        if (request == null) {
            throw new MonitoringException("上报数据不能为空");
        }
        String metricType = normalizeMetricType(request.getMetricType());
        AssetDeviceSensor sensor = requireSensorForPoint(request.getMonitorPointId(), metricType);
        return save(sensor, metricType, request.getUnit(), request.getMetricValue(), request.getCollectTime(), request.getProjectId());
    }

    @Transactional(rollbackFor = Exception.class)
    public EnvBatchPushResult reportBatch(EnvDataBatchReportRequest request) {
        if (request == null || request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new MonitoringException("records 不能为空");
        }
        if (request.getRecords().size() > 500) {
            throw new MonitoringException("单次最多上报500条环境数据");
        }
        int success = 0;
        int failed = 0;
        boolean alarm = false;
        for (EnvDataReportRequest record : request.getRecords()) {
            try {
                EnvDataPushResult result = report(record);
                success++;
                alarm |= result.isAlarmTriggered();
            } catch (MonitoringException ex) {
                failed++;
            }
        }
        return new EnvBatchPushResult(success, failed, alarm);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long reportManual(EnvDataManualRequest request) {
        if (request == null || !StringUtils.hasText(request.getOperator())) {
            throw new MonitoringException("手动补录必须填写操作人");
        }
        return report(request).getId();
    }

    public List<EnvMonitorRecordVO> latest(Long monitorPointId, String metricType) {
        String normalized = normalizeOptionalMetricType(metricType);
        return enrich(dataMapper.selectLatest(monitorPointId, normalized));
    }

    public PageResult<EnvMonitorRecordVO> history(Long monitorPointId, String metricType,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   long pageNum, long pageSize) {
        requirePositivePage(pageNum, pageSize, 500);
        if (monitorPointId == null) throw new MonitoringException("monitorPointId 不能为空");
        String normalized = normalizeMetricType(metricType);
        validateTimeRange(startTime, endTime);
        long total = dataMapper.countHistory(monitorPointId, normalized, startTime, endTime);
        List<EnvMonitorRecordVO> records = enrich(dataMapper.selectHistory(monitorPointId, normalized, startTime, endTime,
                (pageNum - 1) * pageSize, pageSize));
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize, records);
    }

    public PageResult<EnvMonitorRecordVO> alarms(Integer status, Long monitorPointId, String metricType,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  long pageNum, long pageSize) {
        requirePositivePage(pageNum, pageSize, 500);
        if (status != null && (status < 1 || status > 2)) {
            throw new MonitoringException("告警状态必须为1或2");
        }
        String normalized = normalizeOptionalMetricType(metricType);
        validateOptionalTimeRange(startTime, endTime);
        long total = dataMapper.countAlarms(status, monitorPointId, normalized, startTime, endTime);
        List<EnvMonitorRecordVO> records = enrich(dataMapper.selectAlarms(status, monitorPointId, normalized,
                startTime, endTime, (pageNum - 1) * pageSize, pageSize));
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize, records);
    }

    public PageResult<EnvDailyStatVO> dailyStats(Long monitorPointId, LocalDate startDate, LocalDate endDate,
                                                  String metricType, long pageNum, long pageSize) {
        requirePositivePage(pageNum, pageSize, 200);
        validateOptionalDateRange(startDate, endDate);
        String normalized = normalizeOptionalMetricType(metricType);
        long total = dataMapper.countDailyStats(monitorPointId, normalized, startDate, endDate);
        List<EnvDailyStatVO> records = dataMapper.selectDailyStats(monitorPointId, normalized, startDate, endDate,
                (pageNum - 1) * pageSize, pageSize);
        records.forEach(this::enrich);
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize, records);
    }

    public EnvTrendVO trend(Long monitorPointId, LocalDate startDate, LocalDate endDate, String metricType) {
        if (monitorPointId == null) throw new MonitoringException("monitorPointId 不能为空");
        validateDateRange(startDate, endDate);
        String normalized = normalizeMetricType(metricType);
        AssetDeviceSensor sensor = requireSensorForPoint(monitorPointId, normalized);
        EnvTrendVO result = new EnvTrendVO();
        result.setMonitorPointId(monitorPointId);
        result.setPointName(sensor.getSensorName());
        result.setMetricType(normalized);
        result.setUnit(sensor.getUnit());
        result.setTrend(dataMapper.selectTrend(monitorPointId, normalized, startDate, endDate));
        return result;
    }

    public List<EnvThresholdVO> thresholds(Long monitorPointId) {
        List<AssetDeviceSensor> sensors = sensorMapper.selectList(new LambdaQueryWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getIsDeleted, 0)
                .eq(AssetDeviceSensor::getParentType, 2)
                .eq(monitorPointId != null, AssetDeviceSensor::getParentId, monitorPointId)
                .in(AssetDeviceSensor::getMonitorType, METRIC_TYPES)
                .orderByAsc(AssetDeviceSensor::getId));
        return sensors.stream().map(sensor -> {
            EnvThresholdVO vo = new EnvThresholdVO();
            vo.setMonitorPointId(sensor.getParentId());
            vo.setSensorId(sensor.getId());
            vo.setSensorName(sensor.getSensorName());
            vo.setMetricType(sensor.getMonitorType());
            vo.setWarningUpper(sensor.getWarningUpper());
            vo.setWarningLower(sensor.getWarningLower());
            vo.setAlarmUpper(sensor.getAlarmUpper());
            vo.setAlarmLower(sensor.getAlarmLower());
            vo.setSprayThreshold(sensor.getSprayThreshold());
            vo.setUnit(sensor.getUnit());
            return vo;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateThreshold(EnvThresholdUpdateRequest request) {
        if (request == null) throw new MonitoringException("阈值参数不能为空");
        AssetDeviceSensor sensor = requireSensor(request.getSensorId());
        if (request.getWarningUpper() != null && request.getAlarmUpper() != null
                && request.getWarningUpper().compareTo(request.getAlarmUpper()) >= 0) {
            throw new MonitoringException("预警上限必须小于报警上限");
        }
        sensorMapper.update(null, new LambdaUpdateWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getId, sensor.getId())
                .eq(AssetDeviceSensor::getIsDeleted, 0)
                .set(AssetDeviceSensor::getWarningUpper, request.getWarningUpper())
                .set(AssetDeviceSensor::getWarningLower, request.getWarningLower())
                .set(AssetDeviceSensor::getAlarmUpper, request.getAlarmUpper())
                .set(AssetDeviceSensor::getAlarmLower, request.getAlarmLower())
                .set(AssetDeviceSensor::getSprayThreshold, request.getSprayThreshold()));
    }

    private EnvDataPushResult save(AssetDeviceSensor sensor, String metricType, String unit,
                                   BigDecimal value, LocalDateTime collectTime, Long projectId) {
        if (value == null || collectTime == null) throw new MonitoringException("监测值和采集时间不能为空");
        int status = determineStatus(sensor, value);
        EnvMonitorData data = new EnvMonitorData();
        data.setMonitorPointId(sensor.getParentId());
        data.setCollectTime(collectTime);
        data.setMetricType(metricType);
        data.setMetricValue(value);
        data.setUnit(StringUtils.hasText(unit) ? unit.trim() : sensor.getUnit());
        data.setStatus(status);
        try {
            dataMapper.insert(data);
        } catch (DuplicateKeyException ex) {
            throw new MonitoringException("该监测点在该指标和采集时间下的数据已存在，请更换 collectTime 后重试");
        }
        if (alarmService != null) {
            String thresholdRange = "预警上限=" + sensor.getWarningUpper() + ", 报警上限=" + sensor.getAlarmUpper();
            alarmService.processEnvironmentAlarm(projectId, null, sensor.getParentId(), metricType, value, status,
                    collectTime, thresholdRange);
        }
        return new EnvDataPushResult(data.getId(), data.getMonitorPointId(), status > 0, status, statusText(status));
    }

    private AssetDeviceSensor requireSensorForPoint(Long pointId, String metricType) {
        AssetDeviceSensor sensor = pointId == null ? null : sensorMapper.selectOne(new LambdaQueryWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getParentType, 2)
                .eq(AssetDeviceSensor::getParentId, pointId)
                .eq(AssetDeviceSensor::getMonitorType, metricType)
                .eq(AssetDeviceSensor::getIsDeleted, 0));
        if (sensor == null) throw new EnvMonitorPointNotFoundException("环境监测点或指标传感器不存在");
        return sensor;
    }

    private AssetDeviceSensor requireSensor(Long id) {
        AssetDeviceSensor sensor = id == null ? null : sensorMapper.selectOne(new LambdaQueryWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getId, id).eq(AssetDeviceSensor::getParentType, 2).eq(AssetDeviceSensor::getIsDeleted, 0));
        if (sensor == null) throw new EnvMonitorPointNotFoundException("环境传感器不存在或已删除");
        return sensor;
    }

    private void ensureMetricMatches(AssetDeviceSensor sensor, String metricType) {
        if (!metricType.equals(sensor.getMonitorType())) {
            throw new MonitoringException("上报指标与监测点配置不一致");
        }
    }

    private int determineStatus(AssetDeviceSensor sensor, BigDecimal value) {
        if (sensor.getAlarmUpper() != null && value.compareTo(sensor.getAlarmUpper()) >= 0) return 2;
        if (sensor.getAlarmLower() != null && value.compareTo(sensor.getAlarmLower()) <= 0) return 2;
        if (sensor.getWarningUpper() != null && value.compareTo(sensor.getWarningUpper()) >= 0) return 1;
        if (sensor.getWarningLower() != null && value.compareTo(sensor.getWarningLower()) <= 0) return 1;
        return 0;
    }

    private List<EnvMonitorRecordVO> enrich(List<EnvMonitorRecordVO> records) {
        if (records != null) records.forEach(this::enrich);
        return records;
    }

    private void enrich(EnvMonitorRecordVO record) {
        record.setMetricTypeText(METRIC_TEXT.getOrDefault(record.getMetricType(), record.getMetricType()));
        record.setStatusText(statusText(record.getStatus() == null ? 0 : record.getStatus()));
    }

    private void enrich(EnvDailyStatVO record) {
        record.setMetricTypeText(METRIC_TEXT.getOrDefault(record.getMetricType(), record.getMetricType()));
    }

    private String normalizeMetricType(String metricType) {
        String normalized = metricType == null ? null : metricType.trim().toUpperCase(Locale.ROOT);
        if (!METRIC_TYPES.contains(normalized)) throw new MonitoringException("指标类型不支持");
        return normalized;
    }

    private String normalizeOptionalMetricType(String metricType) {
        return StringUtils.hasText(metricType) ? normalizeMetricType(metricType) : null;
    }

    private String statusText(int status) { return status == 2 ? "报警" : status == 1 ? "预警" : "正常"; }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) throw new MonitoringException("开始时间和结束时间不能为空");
        if (end.isBefore(start)) throw new MonitoringException("结束时间不能早于开始时间");
    }

    private void validateOptionalTimeRange(LocalDateTime start, LocalDateTime end) {
        if ((start == null) != (end == null)) throw new MonitoringException("开始时间和结束时间必须同时提供");
        if (start != null) validateTimeRange(start, end);
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) throw new MonitoringException("开始日期和结束日期不能为空");
        if (end.isBefore(start)) throw new MonitoringException("结束日期不能早于开始日期");
    }

    private void validateOptionalDateRange(LocalDate start, LocalDate end) {
        if ((start == null) != (end == null)) throw new MonitoringException("开始日期和结束日期必须同时提供");
        if (start != null) validateDateRange(start, end);
    }

    private void requirePositivePage(long pageNum, long pageSize, long maxSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > maxSize) throw new MonitoringException("分页参数不合法");
    }

    private long pages(long total, long size) { return total == 0 ? 0 : (total + size - 1) / size; }
}
