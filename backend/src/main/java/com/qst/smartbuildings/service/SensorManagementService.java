package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.AssetDeviceType;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.dto.SensorCreateRequest;
import com.qst.smartbuildings.dto.SensorUpdateRequest;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.exception.SensorNotFoundException;
import com.qst.smartbuildings.exception.SensorParentNotFoundException;
import com.qst.smartbuildings.mapper.AssetDeviceLocationMapper;
import com.qst.smartbuildings.mapper.AssetDeviceMapper;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.AssetDeviceTypeMapper;
import com.qst.smartbuildings.vo.SensorPageVO;
import com.qst.smartbuildings.vo.SensorVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SensorManagementService {
    private static final int NO_PARENT = 0;
    private static final int DEVICE_PARENT = 1;
    private static final int ENV_MONITOR_PARENT = 3;
    private static final Set<String> SUPPORTED_MONITOR_TYPES = Set.of(
            "load", "moment", "moment_percent", "wind_speed", "amplitude", "height",
            "load_percent", "passenger_count", "speed", "door_interlock",
            "PM25", "PM10", "NOISE", "TEMP", "HUMIDITY", "WIND");
    private final AssetDeviceSensorMapper sensorMapper;
    private final AssetDeviceMapper assetDeviceMapper;
    private final AssetDeviceTypeMapper assetDeviceTypeMapper;
    private final AssetDeviceLocationMapper assetDeviceLocationMapper;
    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public SensorManagementService(AssetDeviceSensorMapper sensorMapper,
                                   AssetDeviceMapper assetDeviceMapper,
                                   AssetDeviceTypeMapper assetDeviceTypeMapper,
                                   AssetDeviceLocationMapper assetDeviceLocationMapper) {
        this.sensorMapper = sensorMapper;
        this.assetDeviceMapper = assetDeviceMapper;
        this.assetDeviceTypeMapper = assetDeviceTypeMapper;
        this.assetDeviceLocationMapper = assetDeviceLocationMapper;
    }

    public SensorPageVO page(long pageNum, long pageSize, String sensorName, Long parentId, Integer parentType,
                             String monitorType, Integer status) {
        Page<AssetDeviceSensor> page = sensorMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AssetDeviceSensor>()
                        .eq(AssetDeviceSensor::getIsDeleted, 0)
                        .like(StringUtils.hasText(sensorName), AssetDeviceSensor::getSensorName, sensorName)
                        .eq(parentId != null, AssetDeviceSensor::getParentId, parentId)
                        .eq(parentType != null, AssetDeviceSensor::getParentType, parentType)
                        .eq(StringUtils.hasText(monitorType), AssetDeviceSensor::getMonitorType, monitorType)
                        .eq(status != null, AssetDeviceSensor::getStatus, status)
                        .orderByDesc(AssetDeviceSensor::getCreateTime));
        return new SensorPageVO(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toVo).toList());
    }

    public SensorVO detail(Long id) {
        return toVo(requireActive(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SensorCreateRequest request) {
        String monitorType = normalizeMonitorType(request.getMonitorType());
        int parentType = normalizeParentType(request.getParentType());
        Long parentId = normalizeAndValidateParentId(parentType, request.getParentId());
        validateThresholds(request.getWarningLower(), request.getWarningUpper(), request.getAlarmLower(), request.getAlarmUpper());
        AssetDeviceSensor sensor = new AssetDeviceSensor();
        sensor.setSensorName(request.getSensorName().trim());
        sensor.setParentId(parentId);
        sensor.setParentType(parentType);
        sensor.setMonitorType(monitorType);
        sensor.setUnit(request.getUnit().trim());
        sensor.setWarningUpper(request.getWarningUpper());
        sensor.setWarningLower(request.getWarningLower());
        sensor.setAlarmUpper(request.getAlarmUpper());
        sensor.setAlarmLower(request.getAlarmLower());
        sensor.setSprayThreshold(request.getSprayThreshold());
        sensor.setRefreshRate(request.getRefreshRate() == null ? 5 : request.getRefreshRate());
        sensor.setStatus(1);
        sensor.setIsDeleted(0);
        sensorMapper.insert(sensor);

        // 联动创建设备台账记录
        createLinkedDevice(sensor);

        return sensor.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SensorUpdateRequest request) {
        AssetDeviceSensor existing = requireActive(request.getId());
        String monitorType = request.getMonitorType() == null ? null : normalizeMonitorType(request.getMonitorType());
        validateThresholds(request.getWarningLower(), request.getWarningUpper(), request.getAlarmLower(), request.getAlarmUpper());
        AssetDeviceSensor update = new AssetDeviceSensor();
        update.setId(request.getId());
        update.setSensorName(request.getSensorName() == null ? null : request.getSensorName().trim());
        update.setMonitorType(monitorType);
        update.setUnit(request.getUnit() == null ? null : request.getUnit().trim());
        update.setWarningUpper(request.getWarningUpper());
        update.setWarningLower(request.getWarningLower());
        update.setAlarmUpper(request.getAlarmUpper());
        update.setAlarmLower(request.getAlarmLower());
        update.setSprayThreshold(request.getSprayThreshold());
        update.setRefreshRate(request.getRefreshRate());
        sensorMapper.updateById(update);
        updateLinkedDevice(existing, request);
    }

    private void updateLinkedDevice(AssetDeviceSensor existing, SensorUpdateRequest request) {
        AssetDeviceType sensorType = assetDeviceTypeMapper.selectOne(new LambdaQueryWrapper<AssetDeviceType>()
                .eq(AssetDeviceType::getTypeCode, "SENSOR")
                .eq(AssetDeviceType::getIsDeleted, 0));
        if (sensorType == null) {
            return;
        }
        Device deviceUpdate = new Device();
        if (StringUtils.hasText(request.getSensorName())) {
            deviceUpdate.setDeviceName(request.getSensorName().trim());
        }
        deviceUpdate.setUpdateTime(LocalDateTime.now());
        assetDeviceMapper.update(deviceUpdate, new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceName, existing.getSensorName())
                .eq(Device::getTypeId, sensorType.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        requireActive(id);
        if (status == null || status < 0 || status > 2) {
            throw new MonitoringException("监测点状态必须为0、1或2");
        }
        sensorMapper.update(null, new LambdaUpdateWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getId, id).eq(AssetDeviceSensor::getIsDeleted, 0)
                .set(AssetDeviceSensor::getStatus, status));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new MonitoringException("至少需要指定一个监测点");
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new MonitoringException("监测点ID不能为空");
        }
        long count = sensorMapper.selectCount(new LambdaQueryWrapper<AssetDeviceSensor>()
                .in(AssetDeviceSensor::getId, distinctIds).eq(AssetDeviceSensor::getIsDeleted, 0));
        if (count != distinctIds.size()) {
            throw new MonitoringException("存在不存在或已删除的监测点");
        }
        List<AssetDeviceSensor> sensors = sensorMapper.selectList(new LambdaQueryWrapper<AssetDeviceSensor>()
                .in(AssetDeviceSensor::getId, distinctIds).eq(AssetDeviceSensor::getIsDeleted, 0));
        sensorMapper.update(null, new LambdaUpdateWrapper<AssetDeviceSensor>()
                .in(AssetDeviceSensor::getId, distinctIds)
                .set(AssetDeviceSensor::getIsDeleted, 1).set(AssetDeviceSensor::getStatus, 0));
        for (AssetDeviceSensor sensor : sensors) {
            deleteLinkedDevice(sensor);
        }
    }

    private void deleteLinkedDevice(AssetDeviceSensor sensor) {
        AssetDeviceType sensorType = assetDeviceTypeMapper.selectOne(new LambdaQueryWrapper<AssetDeviceType>()
                .eq(AssetDeviceType::getTypeCode, "SENSOR")
                .eq(AssetDeviceType::getIsDeleted, 0));
        if (sensorType == null) {
            return;
        }
        assetDeviceMapper.delete(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceName, sensor.getSensorName())
                .eq(Device::getTypeId, sensorType.getId()));
    }

    private AssetDeviceSensor requireActive(Long id) {
        if (id == null) throw new MonitoringException("监测点ID不能为空");
        AssetDeviceSensor sensor = sensorMapper.selectOne(new LambdaQueryWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getId, id).eq(AssetDeviceSensor::getIsDeleted, 0));
        if (sensor == null) throw new SensorNotFoundException("传感器不存在或已删除");
        return sensor;
    }

    private void validateThresholds(java.math.BigDecimal warningLower, java.math.BigDecimal warningUpper,
                                    java.math.BigDecimal alarmLower, java.math.BigDecimal alarmUpper) {
        if (warningLower != null && warningUpper != null && warningLower.compareTo(warningUpper) > 0) {
            throw new MonitoringException("预警下限不能大于预警上限");
        }
        if (alarmLower != null && alarmUpper != null && alarmLower.compareTo(alarmUpper) > 0) {
            throw new MonitoringException("报警下限不能大于报警上限");
        }
        if (warningUpper != null && alarmUpper != null && warningUpper.compareTo(alarmUpper) >= 0) {
            throw new MonitoringException("预警上限必须严格小于报警上限");
        }
        if (alarmLower != null && warningLower != null && alarmLower.compareTo(warningLower) >= 0) {
            throw new MonitoringException("报警下限必须严格小于预警下限");
        }
    }

    private String normalizeMonitorType(String monitorType) {
        String normalized = monitorType == null ? null : monitorType.trim();
        if (!StringUtils.hasText(normalized) || !SUPPORTED_MONITOR_TYPES.contains(normalized)) {
            throw new MonitoringException("监测类型不支持，请使用已定义的指标类型");
        }
        return normalized;
    }

    private int normalizeParentType(Integer parentType) {
        if (parentType == null || parentType < NO_PARENT || parentType > ENV_MONITOR_PARENT) {
            throw new MonitoringException("父级类型必须为0、1、2或3");
        }
        return parentType;
    }

    private Long normalizeAndValidateParentId(int parentType, Long parentId) {
        if (parentType == NO_PARENT) return 0L;
        if (parentId == null || parentId <= 0) {
            throw new MonitoringException("父级类型为设备或环境监测点时，父级ID必须为正整数");
        }
        // 父级类型 1 关联设备，2、3 关联环境监测点。
        boolean exists = parentType == DEVICE_PARENT
                ? sensorMapper.existsActiveDevice(parentId)
                : sensorMapper.existsEnvMonitorPoint(parentId);
        if (!exists) {
            String parentName = parentType == DEVICE_PARENT ? "设备" : "环境监测点";
            throw new SensorParentNotFoundException(parentName + "不存在或已删除");
        }
        return parentId;
    }
    private void createLinkedDevice(AssetDeviceSensor sensor) {
        // Device-table linkage is optional in lightweight test contexts.
        if (assetDeviceTypeMapper == null || assetDeviceMapper == null || assetDeviceLocationMapper == null) {
            return;
        }
        AssetDeviceType sensorType = assetDeviceTypeMapper.selectOne(new LambdaQueryWrapper<AssetDeviceType>()
                .eq(AssetDeviceType::getTypeCode, "SENSOR")
                .eq(AssetDeviceType::getIsDeleted, 0));
        if (sensorType == null) {
            return;
        }

        Long locationId = null;
        if (sensor.getParentType() != null && sensor.getParentType() == 1 && sensor.getParentId() != null) {
            Device parentDevice = assetDeviceMapper.selectById(sensor.getParentId());
            if (parentDevice != null) {
                locationId = parentDevice.getLocationId();
            }
        }

        if (locationId == null || locationId == 0L) {
            AssetDeviceLocation location = assetDeviceLocationMapper.selectOne(
                    new LambdaQueryWrapper<AssetDeviceLocation>()
                            .eq(AssetDeviceLocation::getIsDeleted, 0)
                            .last("LIMIT 1"));
            if (location == null) {
                return;
            }
            locationId = location.getId();
        }

        Device device = new Device();
        device.setDeviceName(sensor.getSensorName());
        device.setTypeId(sensorType.getId());
        device.setLocationId(locationId);
        device.setBrand("");
        device.setModel("");
        device.setProductionDate(LocalDate.now());
        device.setSupplyDate(LocalDate.now());
        device.setAcceptanceDate(LocalDate.now());
        device.setInstallDate(LocalDate.now());
        device.setEnableDate(LocalDate.now());
        device.setOnlineStatus(0);
        device.setEnableStatus(1);
        device.setIsDeleted(0);
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());
        device.setDeviceCode(generateDeviceCode(sensorType));
        assetDeviceMapper.insert(device);
    }

    private String generateDeviceCode(AssetDeviceType type) {
        String prefix = StringUtils.hasText(type.getTypeCode()) ? type.getTypeCode() : "DEV";
        String timePart = LocalDateTime.now().format(CODE_TIME_FORMAT);
        long seq = assetDeviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getTypeId, type.getId())) + 1;
        while (true) {
            String code = String.format("%s-%s-%04d", prefix, timePart, seq);
            Long exists = assetDeviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                    .eq(Device::getDeviceCode, code));
            if (exists == 0) {
                return code;
            }
            seq++;
        }
    }

    private SensorVO toVo(AssetDeviceSensor sensor) {
        SensorVO vo = new SensorVO();
        vo.setId(sensor.getId());
        vo.setSensorName(sensor.getSensorName());
        vo.setParentId(sensor.getParentId());
        vo.setParentType(sensor.getParentType() == null ? NO_PARENT : sensor.getParentType());
        vo.setParentName(sensorMapper.selectParentName(sensor.getId()));
        vo.setMonitorType(sensor.getMonitorType());
        vo.setUnit(sensor.getUnit());
        vo.setWarningUpper(sensor.getWarningUpper());
        vo.setWarningLower(sensor.getWarningLower());
        vo.setAlarmUpper(sensor.getAlarmUpper());
        vo.setAlarmLower(sensor.getAlarmLower());
        vo.setSprayThreshold(sensor.getSprayThreshold());
        vo.setRefreshRate(sensor.getRefreshRate());
        vo.setStatus(sensor.getStatus());
        vo.setCreateTime(sensor.getCreateTime());
        return vo;
    }
}
