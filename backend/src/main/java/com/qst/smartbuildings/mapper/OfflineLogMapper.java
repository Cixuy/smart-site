package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.OfflineLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface OfflineLogMapper extends BaseMapper<OfflineLog> {

    /**
     * 离线统计（全局或按设备）
     * 统计指定时间段内的离线次数、总时长、平均时长、最长单次时长。
     * 注意：asset_device_offline_log 表不含 is_deleted 列，故不追加逻辑删除过滤。
     *
     * @param deviceId  设备 ID，传 null 表示全局统计
     * @param beginTime 开始时间
     * @param endTime   结束时间
     * @return 含 offlineCount / totalSeconds / avgSeconds / maxSeconds 的 Map
     */
    @Select("SELECT COUNT(*) AS offlineCount, " +
            "COALESCE(SUM(duration_seconds), 0) AS totalSeconds, " +
            "COALESCE(AVG(duration_seconds), 0) AS avgSeconds, " +
            "COALESCE(MAX(duration_seconds), 0) AS maxSeconds " +
            "FROM asset_device_offline_log " +
            "WHERE (#{deviceId} IS NULL OR device_id = #{deviceId}) " +
            "AND offline_time BETWEEN #{beginTime} AND #{endTime}")
    Map<String, Object> selectStatsRaw(@Param("deviceId") Long deviceId,
                                       @Param("beginTime") LocalDateTime beginTime,
                                       @Param("endTime") LocalDateTime endTime);
}
