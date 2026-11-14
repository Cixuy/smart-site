package com.qst.smartbuildings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alibaba.fastjson2.JSON;
import com.qst.smartbuildings.common.EquipmentTypeConst;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.domain.ScreenConfig;
import com.qst.smartbuildings.domain.ScreenVideoRotation;
import com.qst.smartbuildings.dto.ScreenConfigSaveRequest;
import com.qst.smartbuildings.dto.ScreenVideoRotationSaveRequest;
import com.qst.smartbuildings.exception.DeviceStatsException;
import com.qst.smartbuildings.mapper.ScreenConfigMapper;
import com.qst.smartbuildings.mapper.ScreenDataMapper;
import com.qst.smartbuildings.mapper.ScreenVideoRotationMapper;
import com.qst.smartbuildings.service.ScreenService;
import com.qst.smartbuildings.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ScreenServiceImpl implements ScreenService {
    private final ScreenConfigMapper screenConfigMapper;
    private final ScreenVideoRotationMapper screenVideoRotationMapper;
    private final ScreenDataMapper screenDataMapper;

    public ScreenServiceImpl(ScreenConfigMapper screenConfigMapper,
                             ScreenVideoRotationMapper screenVideoRotationMapper,
                             ScreenDataMapper screenDataMapper) {
        this.screenConfigMapper = screenConfigMapper;
        this.screenVideoRotationMapper = screenVideoRotationMapper;
        this.screenDataMapper = screenDataMapper;
    }

    @Override
    public ScreenConfigListData listConfigs(Long projectId, Integer status) {
        LambdaQueryWrapper<ScreenConfig> wrapper = Wrappers.lambdaQuery(ScreenConfig.class)
                .eq(ScreenConfig::getProjectId, projectId)
                .orderByDesc(ScreenConfig::getIsDefault)
                .orderByAsc(ScreenConfig::getId);
        if (status != null) {
            wrapper.eq(ScreenConfig::getStatus, status);
        }
        List<ScreenConfigVO> records = screenConfigMapper.selectList(wrapper).stream().map(this::toVO).toList();
        return new ScreenConfigListData(records);
    }

    @Override
    @Transactional
    public Long createConfig(ScreenConfigSaveRequest request) {
        ScreenConfig config = new ScreenConfig();
        fillConfig(config, request);
        screenConfigMapper.insert(config);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            screenConfigMapper.clearProjectDefault(config.getProjectId(), config.getId());
            config.setIsDefault(1);
            screenConfigMapper.updateById(config);
        }
        return config.getId();
    }

    @Override
    public ScreenConfigVO getConfig(Long screenId) {
        ScreenConfig config = mustGetConfig(screenId);
        return toVO(config);
    }

    @Override
    public boolean updateConfig(Long screenId, ScreenConfigSaveRequest request) {
        ScreenConfig config = mustGetConfig(screenId);
        fillConfig(config, request);
        boolean updated = screenConfigMapper.updateById(config) > 0;
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            screenConfigMapper.clearProjectDefault(config.getProjectId(), config.getId());
            config.setIsDefault(1);
            screenConfigMapper.updateById(config);
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean deleteConfig(Long screenId) {
        mustGetConfig(screenId);
        screenVideoRotationMapper.delete(Wrappers.<ScreenVideoRotation>lambdaQuery()
                .eq(ScreenVideoRotation::getScreenId, screenId));
        return screenConfigMapper.deleteById(screenId) > 0;
    }

    @Override
    @Transactional
    public boolean setDefaultScreen(Long screenId) {
        ScreenConfig config = mustGetConfig(screenId);
        if (config.getStatus() == null || config.getStatus() != 1) {
            throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "只有启用中的大屏才能设置为默认");
        }
        screenConfigMapper.clearProjectDefault(config.getProjectId(), config.getId());
        config.setIsDefault(1);
        return screenConfigMapper.updateById(config) > 0;
    }

    @Override
    public ScreenRealtimeData getRealtimeData(Long screenId) {
        mustGetConfig(screenId);
        ScreenRealtimeData data = new ScreenRealtimeData();
        data.setDeviceStatus(screenDataMapper.selectDeviceStatus());
        data.setAlarmScroll(screenDataMapper.selectRecentAlarms());
        data.setAlarmTrend(screenDataMapper.selectAlarmTrend());
        data.setEnvironmentMetrics(screenDataMapper.selectEnvironmentMetrics());
        data.setVideoRotations(screenVideoRotationMapper.selectRotationVOs(screenId));
        data.setTowerCraneStatus(buildEquipmentStatuses((Integer) EquipmentTypeConst.TOWER_TYPE_ID));
        data.setHoistStatus(buildEquipmentStatuses((Integer) EquipmentTypeConst.ELEVATOR_TYPE_ID));
        data.setServerTime(OffsetDateTime.now(ZoneId.of("Asia/Shanghai")));
        return data;
    }

    @Override
    public ScreenVideoRotationListData listVideoRotations(Long screenId) {
        mustGetConfig(screenId);
        return new ScreenVideoRotationListData(screenVideoRotationMapper.selectRotationVOs(screenId));
    }

    @Override
    @Transactional
    public boolean saveVideoRotations(Long screenId, List<ScreenVideoRotationSaveRequest> request) {
        mustGetConfig(screenId);
        screenVideoRotationMapper.delete(Wrappers.<ScreenVideoRotation>lambdaQuery().eq(ScreenVideoRotation::getScreenId, screenId));
        if (request == null || request.isEmpty()) {
            return true;
        }
        List<ScreenVideoRotation> records = new ArrayList<>();
        for (ScreenVideoRotationSaveRequest item : request) {
            ScreenVideoRotation rotation = new ScreenVideoRotation();
            rotation.setScreenId(screenId);
            rotation.setCameraId(item.getCameraId());
            rotation.setDisplayOrder(item.getDisplayOrder());
            rotation.setDuration(item.getDuration());
            records.add(rotation);
        }
        records.sort(Comparator.comparing(ScreenVideoRotation::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)));
        for (ScreenVideoRotation rotation : records) {
            screenVideoRotationMapper.insert(rotation);
        }
        return true;
    }

    private void fillConfig(ScreenConfig config, ScreenConfigSaveRequest request) {
        config.setScreenName(request.getScreenName());
        config.setProjectId(request.getProjectId());
        config.setLayoutConfig(JSON.toJSONString(request.getLayoutConfig()));
        config.setRefreshInterval(request.getRefreshInterval());
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0);
        config.setStatus(request.getStatus());
    }

    private ScreenConfigVO toVO(ScreenConfig config) {
        ScreenConfigVO vo = new ScreenConfigVO();
        vo.setId(config.getId());
        vo.setScreenName(config.getScreenName());
        vo.setProjectId(config.getProjectId());
        vo.setLayoutConfig(JSON.parseObject(config.getLayoutConfig(), ScreenLayoutConfig.class));
        vo.setRefreshInterval(config.getRefreshInterval());
        vo.setIsDefault(config.getIsDefault() != null && config.getIsDefault() == 1);
        vo.setStatus(config.getStatus());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    private ScreenConfig mustGetConfig(Long screenId) {
        ScreenConfig config = screenConfigMapper.selectById(screenId);
        if (config == null) {
            throw new DeviceStatsException(SystemCodeEnum.NOT_FOUND, "数据大屏不存在");
        }
        return config;
    }

    private List<EquipmentRuntimeStatus> buildEquipmentStatuses(Integer typeId) {
        List<java.util.Map<String, Object>> rows = screenDataMapper.selectEquipmentMetrics(typeId);
        List<EquipmentRuntimeStatus> statuses = new ArrayList<>();
        EquipmentRuntimeStatus current = null;
        Long currentDeviceId = null;
        for (java.util.Map<String, Object> row : rows) {
            Long deviceId = row.get("deviceId") == null ? null : ((Number) row.get("deviceId")).longValue();
            if (current == null || !java.util.Objects.equals(currentDeviceId, deviceId)) {
                current = new EquipmentRuntimeStatus();
                current.setDeviceId(deviceId);
                current.setDeviceName((String) row.get("deviceName"));
                current.setOnlineStatus(row.get("onlineStatus") == null ? null : ((Number) row.get("onlineStatus")).intValue());
                statuses.add(current);
                currentDeviceId = deviceId;
            }
            MetricValue metric = new MetricValue();
            metric.setName((String) row.get("name"));
            Object value = row.get("value");
            metric.setValue(value instanceof Number n ? n.doubleValue() : 0d);
            metric.setUnit((String) row.get("unit"));
            Object alarm = row.get("alarm");
            metric.setAlarm(alarm instanceof Boolean b && b);
            current.getMetrics().add(metric);
        }
        return statuses;
    }
}
