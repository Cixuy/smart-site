package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.vo.LocationMapVO;
import com.qst.smartbuildings.vo.LocationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备位置 Mapper
 */
@Mapper
public interface AssetDeviceLocationMapper extends BaseMapper<AssetDeviceLocation> {

    /**
     * 平铺查询位置列表（带负责人姓名）
     * 动态拼接筛选条件
     */
    @Select("<script>"
            + "SELECT l.id, l.location_name AS locationName, l.parent_id AS parentId, l.level, "
            + "       l.gis_lon AS gisLon, l.gis_lat AS gisLat, "
            + "       l.responsible_user_id AS responsibleUserId, "
            + "       u.real_name AS responsibleUserName, "
            + "       l.sort, l.status, l.create_time AS createTime "
            + "FROM asset_device_location l "
            + "LEFT JOIN sys_user u ON u.id = l.responsible_user_id AND u.is_deleted = 0 "
            + "WHERE l.is_deleted = 0 "
            + "<if test='locationName != null and locationName != \"\"'> AND l.location_name LIKE CONCAT('%', #{locationName}, '%') </if>"
            + "<if test='status != null'> AND l.status = #{status} </if>"
            + "<if test='responsibleUserId != null'> AND l.responsible_user_id = #{responsibleUserId} </if>"
            + "ORDER BY l.sort ASC, l.id ASC"
            + "</script>")
    List<LocationVO> selectLocationList(@Param("locationName") String locationName,
                                        @Param("status") Integer status,
                                        @Param("responsibleUserId") Long responsibleUserId);

    /**
     * 查询单个位置详情（带负责人姓名）
     */
    @Select("SELECT l.id, l.location_name AS locationName, l.parent_id AS parentId, l.level, "
            + "       l.gis_lon AS gisLon, l.gis_lat AS gisLat, "
            + "       l.responsible_user_id AS responsibleUserId, "
            + "       u.real_name AS responsibleUserName, "
            + "       l.sort, l.status, l.create_time AS createTime "
            + "FROM asset_device_location l "
            + "LEFT JOIN sys_user u ON u.id = l.responsible_user_id AND u.is_deleted = 0 "
            + "WHERE l.id = #{id} AND l.is_deleted = 0")
    LocationVO selectLocationById(@Param("id") Long id);

    /**
     * 查询所有启用位置的坐标及该位置下的设备数量（用于地图/3D打点）
     */
    @Select("SELECT l.id, l.location_name AS locationName, l.gis_lon AS gisLon, l.gis_lat AS gisLat, l.level, "
            + "       (SELECT COUNT(*) FROM asset_device d WHERE d.location_id = l.id AND d.is_deleted = 0) AS deviceCount "
            + "FROM asset_device_location l "
            + "WHERE l.is_deleted = 0 AND l.status = 1 "
            + "ORDER BY l.sort ASC, l.id ASC")
    List<LocationMapVO> selectLocationMapData();

    /**
     * 统计某位置下已关联且未删除的设备数量
     */
    @Select("SELECT COUNT(*) FROM asset_device WHERE location_id = #{locationId} AND is_deleted = 0")
    Long countDeviceByLocationId(@Param("locationId") Long locationId);
}
