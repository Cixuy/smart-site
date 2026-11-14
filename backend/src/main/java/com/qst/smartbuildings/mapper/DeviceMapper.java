package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 设备在线率统计（全局或按位置）
     * 仅统计 enable_status=1 且 is_deleted=0 的设备，
     * 按 online_status 拆分 在线/离线/已禁用 三类数量。
     *
     * @param locationId 位置 ID，传 null 表示全局统计
     * @return 含 totalCount / onlineCount / offlineCount / disabledCount 的 Map
     */
    @Select("SELECT COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN online_status = 1 AND enable_status = 1 THEN 1 ELSE 0 END) AS onlineCount, " +
            "SUM(CASE WHEN online_status = 0 AND enable_status = 1 THEN 1 ELSE 0 END) AS offlineCount, " +
            "SUM(CASE WHEN enable_status = 0 THEN 1 ELSE 0 END) AS disabledCount " +
            "FROM asset_device WHERE is_deleted = 0 " +
            "AND (#{locationId} IS NULL OR location_id = #{locationId})")
    Map<String, Object> selectOnlineStats(@Param("locationId") Long locationId);
}
