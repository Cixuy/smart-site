package com.qst.smartbuildings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.domain.OfflineLog;
import com.qst.smartbuildings.entity.BPage;
import com.qst.smartbuildings.exception.DeviceStatsException;
import com.qst.smartbuildings.mapper.DeviceMapper;
import com.qst.smartbuildings.mapper.OfflineLogMapper;
import com.qst.smartbuildings.service.DeviceStatsService;
import com.qst.smartbuildings.vo.DeviceOnlineRateVO;
import com.qst.smartbuildings.vo.DeviceOnlineStatsVO;
import com.qst.smartbuildings.vo.OfflineLogStatsVO;
import com.qst.smartbuildings.vo.OfflineLogVO;
import com.qst.smartbuildings.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeviceStatsServiceImpl implements DeviceStatsService {

    /** 单台设备在线率统计最大时间跨度：365 天 */
    private static final long MAX_RANGE_DAYS = 365L;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private OfflineLogMapper offlineLogMapper;

    @Override
    public DeviceOnlineStatsVO getOnlineStats(Long locationId) {
        Map<String, Object> map = deviceMapper.selectOnlineStats(locationId);
        int total = toInt(map.get("totalCount"));
        int online = toInt(map.get("onlineCount"));
        int offline = toInt(map.get("offlineCount"));
        int disabled = toInt(map.get("disabledCount"));

        DeviceOnlineStatsVO vo = new DeviceOnlineStatsVO();
        vo.setTotalCount(total);
        vo.setOnlineCount(online);
        vo.setOfflineCount(offline);
        vo.setDisabledCount(disabled);
        vo.setOnlineRate(total == 0 ? 0.0 : round1((double) online * 100 / total));
        return vo;
    }

    @Override
    public DeviceOnlineRateVO getDeviceOnlineRate(Long deviceId, String beginTime, String endTime) {
        // 1. 参数校验
        LocalDateTime begin = parseIso(beginTime);
        LocalDateTime end = parseIso(endTime);
        if (begin == null || end == null) {
            throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "开始时间和结束时间必须同时传入");
        }
        if (!end.isAfter(begin)) {
            throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        long days = ChronoUnit.DAYS.between(begin, end);
        if (days > MAX_RANGE_DAYS) {
            throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "时间范围最大支持 365 天");
        }

        // 2. 设备存在性校验
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new DeviceStatsException(SystemCodeEnum.NOT_FOUND, "设备不存在");
        }

        // 3. 统计周期总秒数
        long totalSeconds = ChronoUnit.SECONDS.between(begin, end);

        // 4. 查询与统计窗口重叠的离线记录
        LambdaQueryWrapper<OfflineLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OfflineLog::getDeviceId, deviceId)
                .lt(OfflineLog::getOfflineTime, end)
                .and(i -> i.isNull(OfflineLog::getOnlineTime).or().gt(OfflineLog::getOnlineTime, begin));
        List<OfflineLog> logs = offlineLogMapper.selectList(wrapper);

        // 5. 计算窗口内离线秒数
        long offlineSeconds = 0;
        for (OfflineLog log : logs) {
            LocalDateTime effStart = log.getOfflineTime().isBefore(begin) ? begin : log.getOfflineTime();
            LocalDateTime effEnd;
            if (log.getOnlineTime() == null) {
                effEnd = end; // 仍未恢复，窗口结束即离线终点
            } else {
                effEnd = log.getOnlineTime().isAfter(end) ? end : log.getOnlineTime();
            }
            if (effEnd.isAfter(effStart)) {
                offlineSeconds += ChronoUnit.SECONDS.between(effStart, effEnd);
            }
        }

        long onlineSeconds = totalSeconds - offlineSeconds;
        if (onlineSeconds < 0) {
            onlineSeconds = 0;
        }
        int offlineCount = logs.size();
        long avgOffline = offlineCount == 0 ? 0 : offlineSeconds / offlineCount;

        DeviceOnlineRateVO vo = new DeviceOnlineRateVO();
        vo.setDeviceId(deviceId);
        vo.setDeviceName(device.getDeviceName());
        vo.setTotalSeconds(totalSeconds);
        vo.setOfflineSeconds(offlineSeconds);
        vo.setOnlineSeconds(onlineSeconds);
        vo.setOnlineRate(totalSeconds == 0 ? 0.0 : round1((double) onlineSeconds * 100 / totalSeconds));
        vo.setOfflineCount(offlineCount);
        vo.setAvgOfflineDurationSeconds(avgOffline);
        return vo;
    }

    @Override
    public PageResult<OfflineLogVO> pageOfflineLogs(long pageNum, long pageSize, Long deviceId,
                                                    String reason, String beginTime, String endTime, Integer isOngoing) {
        LocalDateTime begin = parseIsoOptional(beginTime);
        LocalDateTime end = parseIsoOptional(endTime);

        BPage<OfflineLog> page = new BPage<>(pageNum, pageSize);
        LambdaQueryWrapper<OfflineLog> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(OfflineLog::getDeviceId, deviceId);
        }
        if (StringUtils.hasText(reason)) {
            wrapper.eq(OfflineLog::getReason, reason);
        }
        if (begin != null) {
            wrapper.ge(OfflineLog::getOfflineTime, begin);
        }
        if (end != null) {
            wrapper.le(OfflineLog::getOfflineTime, end);
        }
        if (isOngoing != null) {
            if (isOngoing == 1) {
                wrapper.isNull(OfflineLog::getOnlineTime);       // 仅未恢复
            } else if (isOngoing == 0) {
                wrapper.isNotNull(OfflineLog::getOnlineTime);    // 仅已恢复
            }
        }
        wrapper.orderByDesc(OfflineLog::getOfflineTime);          // BR-OFFLINE-001

        offlineLogMapper.selectPage(page, wrapper);

        List<OfflineLogVO> records = fillDeviceInfo(page.getRecords());
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public OfflineLogVO getOfflineLogDetail(Long id) {
        OfflineLog log = offlineLogMapper.selectById(id);
        if (log == null) {
            throw new DeviceStatsException(SystemCodeEnum.NOT_FOUND, "离线记录不存在");
        }
        return fillDeviceInfo(List.of(log)).get(0);
    }

    @Override
    public OfflineLogStatsVO getOfflineStats(Long deviceId, String beginTime, String endTime) {
        LocalDateTime begin = parseIso(beginTime);
        LocalDateTime end = parseIso(endTime);
        if (begin == null || end == null) {
            throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "开始时间和结束时间必须同时传入");
        }
        if (!end.isAfter(begin)) {
            throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "结束时间必须晚于开始时间");
        }

        Map<String, Object> map = offlineLogMapper.selectStatsRaw(deviceId, begin, end);
        long offlineCount = toLong(map.get("offlineCount"));
        long totalSeconds = toLong(map.get("totalSeconds"));
        double avgSeconds = toDouble(map.get("avgSeconds"));
        long maxSeconds = toLong(map.get("maxSeconds"));

        OfflineLogStatsVO vo = new OfflineLogStatsVO();
        vo.setOfflineCount(offlineCount);
        vo.setTotalDurationMinutes((int) (totalSeconds / 60));
        vo.setAvgDurationMinutes(round1(avgSeconds / 60));
        vo.setMaxDurationMinutes((int) (maxSeconds / 60));
        return vo;
    }

    // ==================== 私有方法 ====================

    /** 解析 ISO 8601 时间（必填，解析失败抛异常） */
    private LocalDateTime parseIso(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException e2) {
                throw new DeviceStatsException(SystemCodeEnum.BAD_REQUEST, "时间格式不正确，请使用 ISO 8601 格式（如 2026-08-07T14:30:00）");
            }
        }
    }

    /** 解析 ISO 8601 时间（可选，空串返回 null，格式错误抛异常） */
    private LocalDateTime parseIsoOptional(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return parseIso(s);
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return 0;
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof BigDecimal b) return b.doubleValue();
        return 0.0;
    }

    /** 保留 1 位小数 */
    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /**
     * 关联 asset_device 补全离线记录的 deviceName / deviceCode。
     * 真实表 asset_device_offline_log 不含设备名称/编码列，需按 device_id 回查。
     */
    private List<OfflineLogVO> fillDeviceInfo(List<OfflineLog> logs) {
        Set<Long> deviceIds = logs.stream()
                .map(OfflineLog::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Device> deviceMap = deviceIds.isEmpty()
                ? Collections.emptyMap()
                : deviceMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(Device::getId, d -> d, (a, b) -> a));

        return logs.stream().map(log -> {
            OfflineLogVO vo = OfflineLogVO.from(log);
            Device device = deviceMap.get(log.getDeviceId());
            if (device != null) {
                vo.setDeviceName(device.getDeviceName());
                vo.setDeviceCode(device.getDeviceCode());
            }
            return vo;
        }).toList();
    }
}
