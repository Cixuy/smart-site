package com.qst.smartbuildings.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * [注意] 临时只读 Mapper —— 3005 摄像头模块合并 develop 前临时使用，合并后【整体删除】。
 *
 * <p>仅 SELECT 已存在的 biz_camera 表，不做任何 INSERT/UPDATE/DELETE，避免与 3005 模块（feature/Wormwaker_video-monitoring）冲突。
 * 3005 合并 develop 后，本文件删除，SceneServiceImpl.listVideoOverlays 改用 3005 的 CameraMapper / BizCamera 实体查询。</p>
 *
 * <p>注意：biz_camera.device_id 是「设备编码」(varchar(30)，String)，不是 asset_device.id (bigint)。
 * 桥接路径：device_coordinate_mapping.device_id(bigint) → asset_device.id → asset_device.device_code → biz_camera.device_id。</p>
 */
@Mapper
public interface SceneCameraReadMapper {

    @Select("SELECT stream_url FROM biz_camera " +
            "WHERE device_id = #{deviceCode} AND is_deleted = 0 " +
            "ORDER BY id DESC LIMIT 1")
    String selectStreamUrlByDeviceCode(@Param("deviceCode") String deviceCode);
}
