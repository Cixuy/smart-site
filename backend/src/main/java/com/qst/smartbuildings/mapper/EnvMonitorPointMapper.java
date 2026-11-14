package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.EnvMonitorPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface EnvMonitorPointMapper extends BaseMapper<EnvMonitorPoint> {
    @Select("SELECT id FROM asset_device_sensor WHERE parent_type=2 AND parent_id=#{pointId} AND is_deleted=0 ORDER BY id")
    List<Long> selectSensorIds(@Param("pointId") Long pointId);

    @Select("SELECT COUNT(*) FROM asset_device_sensor WHERE parent_type=2 AND parent_id=#{pointId} AND is_deleted=0 AND status=1")
    long countRunningSensors(@Param("pointId") Long pointId);
}
