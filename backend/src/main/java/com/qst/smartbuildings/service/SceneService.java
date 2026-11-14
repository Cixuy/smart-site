package com.qst.smartbuildings.service;

import com.qst.smartbuildings.dto.DeviceCoordinateCreateDTO;
import com.qst.smartbuildings.dto.DeviceCoordinateUpdateDTO;
import com.qst.smartbuildings.dto.SceneConfigCreateDTO;
import com.qst.smartbuildings.dto.VideoOverlayCreateDTO;
import com.qst.smartbuildings.dto.VideoOverlayUpdateDTO;
import com.qst.smartbuildings.vo.AlarmDeviceVO;
import com.qst.smartbuildings.vo.DeviceCoordinateVO;
import com.qst.smartbuildings.vo.DeviceDetailVO;
import com.qst.smartbuildings.vo.ModelUploadResultVO;
import com.qst.smartbuildings.vo.SceneConfigVO;
import com.qst.smartbuildings.vo.VideoOverlayVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 3D可视化场景模块业务接口
 * <p>Base Path：/api/scene。写操作仅系统管理员（实现内通过 UserContext 校验），读操作已登录即可。</p>
 */
public interface SceneService {

    // ===================== 场景配置 =====================

    /** 3.1.1 按项目查询3D场景配置 */
    SceneConfigVO getByProject(Long projectId);

    /** 3.1.2 新增3D场景配置（管理员） */
    Long createConfig(SceneConfigCreateDTO dto);

    /** 3.1.3 查询3D场景配置详情 */
    SceneConfigVO getConfigDetail(Long sceneId);

    /** 3.1.4 修改3D场景配置（管理员） */
    void updateConfig(Long sceneId, SceneConfigCreateDTO dto);

    /** 3.1.5 删除3D场景配置（管理员，级联删坐标与视频叠加） */
    void deleteConfig(Long sceneId);

    /** 3.1.6 上传设备3D模型文件（管理员）：按设备类型存入 resources/device-models，并登记/更新设备坐标 */
    ModelUploadResultVO uploadModel(Long sceneId, Long deviceId, Integer iconType, MultipartFile file);

    // ===================== 设备坐标映射 =====================

    /** 3.2.1 查询3D场景设备坐标与实时状态（高频） */
    List<DeviceCoordinateVO> listDevices(Long sceneId, Integer iconType);

    /** 3.2.2 新增设备3D坐标映射（管理员） */
    Long createDeviceCoordinate(Long sceneId, DeviceCoordinateCreateDTO dto);

    /** 3.2.3 查询3D设备详情卡片 */
    DeviceDetailVO getDeviceDetail(Long sceneId, Long deviceId);

    /** 3.2.4 修改设备3D坐标映射（管理员） */
    void updateDeviceCoordinate(Long coordinateId, DeviceCoordinateUpdateDTO dto);

    /** 3.2.5 删除设备3D坐标映射（管理员，级联删视频叠加） */
    void deleteDeviceCoordinate(Long coordinateId);

    /** 3.2.6 查询3D场景告警高亮设备 */
    List<AlarmDeviceVO> listAlarmDevices(Long sceneId);

    // ===================== 视频叠加 =====================

    /** 3.3.1 查询3D场景视频叠加配置 */
    List<VideoOverlayVO> listVideoOverlays(Long sceneId);

    /** 3.3.2 新增3D视频叠加配置（管理员） */
    Long createVideoOverlay(VideoOverlayCreateDTO dto);

    /** 3.3.3 修改3D视频叠加配置（管理员） */
    void updateVideoOverlay(Long overlayId, VideoOverlayUpdateDTO dto);

    /** 3.3.4 删除3D视频叠加配置（管理员） */
    void deleteVideoOverlay(Long overlayId);
}
