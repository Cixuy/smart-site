package com.qst.smartbuildings.service;

import com.qst.smartbuildings.vo.DeviceOnlineRateVO;
import com.qst.smartbuildings.vo.DeviceOnlineStatsVO;
import com.qst.smartbuildings.vo.OfflineLogStatsVO;
import com.qst.smartbuildings.vo.OfflineLogVO;
import com.qst.smartbuildings.vo.PageResult;

/**
 * 设备在线/离线统计服务
 */
public interface DeviceStatsService {

    /**
     * 设备在线率统计（全局或按位置）
     * @param locationId 位置 ID，null 表示全局
     */
    DeviceOnlineStatsVO getOnlineStats(Long locationId);

    /**
     * 单设备在线率查询
     * @param deviceId  设备 ID
     * @param beginTime 开始时间（ISO 8601）
     * @param endTime   结束时间（ISO 8601）
     */
    DeviceOnlineRateVO getDeviceOnlineRate(Long deviceId, String beginTime, String endTime);

    /**
     * 分页查询离线记录
     */
    PageResult<OfflineLogVO> pageOfflineLogs(long pageNum, long pageSize, Long deviceId,
                                             String reason, String beginTime, String endTime, Integer isOngoing);

    /**
     * 查询离线记录详情
     * @param id 离线记录 ID
     */
    OfflineLogVO getOfflineLogDetail(Long id);

    /**
     * 获取设备离线统计
     * @param deviceId  设备 ID，null 表示全局
     * @param beginTime 开始时间（ISO 8601）
     * @param endTime   结束时间（ISO 8601）
     */
    OfflineLogStatsVO getOfflineStats(Long deviceId, String beginTime, String endTime);
}
