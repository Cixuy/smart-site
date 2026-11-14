package com.qst.smartbuildings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartbuildings.common.SceneConstants;
import com.qst.smartbuildings.config.ModelStorageProperties;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.common.UserContext;
import com.qst.smartbuildings.domain.DeviceCoordinateMapping;
import com.qst.smartbuildings.domain.Scene3dConfig;
import com.qst.smartbuildings.domain.SceneVideoOverlay;
import com.qst.smartbuildings.dto.DeviceCoordinateCreateDTO;
import com.qst.smartbuildings.dto.DeviceCoordinateUpdateDTO;
import com.qst.smartbuildings.dto.SceneConfigCreateDTO;
import com.qst.smartbuildings.dto.VideoOverlayCreateDTO;
import com.qst.smartbuildings.dto.VideoOverlayUpdateDTO;
import com.qst.smartbuildings.exception.SceneException;
import com.qst.smartbuildings.mapper.DeviceCoordinateMappingMapper;
import com.qst.smartbuildings.mapper.Scene3dConfigMapper;
import com.qst.smartbuildings.mapper.SceneCameraReadMapper;
import com.qst.smartbuildings.mapper.SceneVideoOverlayMapper;
import com.qst.smartbuildings.service.AlarmService;
import com.qst.smartbuildings.service.AssetDeviceService;
import com.qst.smartbuildings.service.HoistService;
import com.qst.smartbuildings.service.SceneService;
import com.qst.smartbuildings.service.TowerCraneService;
import com.qst.smartbuildings.vo.AlarmListVO;
import com.qst.smartbuildings.vo.HoistRealtimeVO;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.TowerCraneRealtimeVO;
import com.qst.smartbuildings.vo.AlarmDeviceVO;
import com.qst.smartbuildings.vo.AlarmItemVO;
import com.qst.smartbuildings.vo.DeviceVO;
import com.qst.smartbuildings.vo.Coordinate3DVO;
import com.qst.smartbuildings.vo.DeviceCoordinateVO;
import com.qst.smartbuildings.vo.DeviceDetailVO;
import com.qst.smartbuildings.vo.ModelUploadResultVO;
import com.qst.smartbuildings.vo.RealtimeMetricVO;
import com.qst.smartbuildings.vo.SceneConfigVO;
import com.qst.smartbuildings.vo.VideoOverlayVO;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 3D可视化场景业务实现
 * <p>认证主体由 AuthInterceptor 写入 UserContext，本类只读取，不解析 token。</p>
 *
 * <p><b>外部依赖说明：</b></p>
 * <ul>
 *   <li>设备名称/编号/在线状态 → 设备资产管理模块（2004 已实现，注入 AssetDeviceService 填充）</li>
 *   <li>实时指标 realtimeMetrics → 塔吊/升降机监控实时数据服务（3002 已推送，调用 TowerCraneService/HoistService.latest，按 device_code 桥接）</li>
 *   <li>告警 latestAlarmLevel / alarms / 高亮列表 → 告警管理模块（4002 已推送，调用 AlarmService.page 按 deviceId 查未处置告警）</li>
 *   <li>视频流 streamUrl → 视频监控模块 biz_camera（3005 尚未就绪，3D 临时用 SceneCameraReadMapper 只读 biz_camera，待 3005 完成后删除）</li>
 *   <li>403「设备不属于当前项目」→ 需 asset_device.project_id 校验（模块未实现，暂跳过）</li>
 * </ul>
 */
@Service
public class SceneServiceImpl implements SceneService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 设备模型仅接受 glTF 二进制（.glb），Web 3D 原生支持，且文件名/扩展名固定便于按约定派生访问地址 */
    private static final Set<String> ALLOWED_MODEL_EXT = Set.of("glb");
    /** 图标类型与视频叠加上限取自 common.SceneConstants */

    private final Scene3dConfigMapper configMapper;
    private final DeviceCoordinateMappingMapper coordMapper;
    private final SceneVideoOverlayMapper overlayMapper;
    private final AssetDeviceService assetDeviceService;
    /** 临时只读 Mapper：3005 摄像头模块尚未就绪前使用，待 3005 完成后删除并改用其 CameraMapper */
    private final SceneCameraReadMapper cameraReadMapper;
    /** 告警模块（4002 已推送）：查询设备未处置告警与场景高亮级别 */
    private final AlarmService alarmService;
    /** 塔吊实时监控服务（3002 已推送）：设备详情实时指标 */
    private final TowerCraneService towerCraneService;
    /** 升降机实时监控服务（3002 已推送）：设备详情实时指标 */
    private final HoistService hoistService;
    /** 设备3D模型存储目录配置（可外部化，避免 jar 内 resources 不可写） */
    private final ModelStorageProperties modelStorageProperties;

    public SceneServiceImpl(Scene3dConfigMapper configMapper,
                            DeviceCoordinateMappingMapper coordMapper,
                            SceneVideoOverlayMapper overlayMapper,
                            AssetDeviceService assetDeviceService,
                            SceneCameraReadMapper cameraReadMapper,
                            AlarmService alarmService,
                            TowerCraneService towerCraneService,
                            HoistService hoistService,
                            ModelStorageProperties modelStorageProperties) {
        this.configMapper = configMapper;
        this.coordMapper = coordMapper;
        this.overlayMapper = overlayMapper;
        this.assetDeviceService = assetDeviceService;
        this.cameraReadMapper = cameraReadMapper;
        this.alarmService = alarmService;
        this.towerCraneService = towerCraneService;
        this.hoistService = hoistService;
        this.modelStorageProperties = modelStorageProperties;
    }

    /**
     * 安全获取设备基础信息（来自资产模块 2004，已实现）。
     * 设备不存在或服务异常时返回 null，避免跨模块调用失败影响 3D 场景展示。
     */
    private DeviceVO safeGetDevice(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        try {
            return assetDeviceService.getDeviceById(deviceId);
        } catch (Exception e) {
            return null;
        }
    }

    // ======================== 权限 ========================

    /** 写操作权限校验：仅系统管理员。auth.enabled=false 联调时拦截器注入超管（含 ADMIN），自然通过。 */
    private void assertAdmin() {
        if (!SceneConstants.isSystemAdmin(UserContext.get())) {
            // SystemCodeEnum.SYSTEM_BAD_REQUEST 的 code 为 403，全局异常处理器映射为 403 FORBIDDEN
            throw new SceneException(SystemCodeEnum.SYSTEM_BAD_REQUEST, "仅系统管理员可操作");
        }
    }

    // ======================== 工具 ========================

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DT_FMT);
    }

    private static boolean isSimple(Integer v) {
        return v != null && v == 1;
    }

    private static boolean isTransparent(Integer v) {
        return v == null || v == 1;
    }

    /** 取场景下的全部设备坐标ID集合（用于视频叠加按场景统计/查询） */
    private Set<Long> sceneCoordinateIds(Long sceneId) {
        return coordMapper.selectList(new LambdaQueryWrapper<DeviceCoordinateMapping>()
                        .eq(DeviceCoordinateMapping::getSceneId, sceneId))
                .stream().map(DeviceCoordinateMapping::getId).collect(Collectors.toSet());
    }

    // ======================== 模型路径派生（约定优于存储） ========================
    // 设备模型访问地址完全由 (projectId, sceneId, iconType, deviceCode) 推算，不落库，避免冗余字段与文件不同步。
    // 物理路径：resources/device-models/{projectId}/{sceneId}/{iconTypeLabel}/{deviceCode}.glb

    /** 图标类型 → 可读文件夹标签 */
    private static String iconTypeLabel(Integer iconType) {
        if (iconType == null) {
            return "unknown";
        }
        return switch (iconType) {
            case SceneConstants.ICON_TOWER_CRANE -> "tower-crane";
            case SceneConstants.ICON_HOIST -> "hoist";
            case SceneConstants.ICON_CAMERA -> "camera";
            case SceneConstants.ICON_ENV_MONITOR -> "env";
            case SceneConstants.ICON_SPRAY -> "spray";
            default -> "unknown";
        };
    }

    /** 设备编码清洗为文件系统安全名称（保留字母数字 . _ -） */
    private static String sanitizeCode(String code) {
        String s = code.trim().replaceAll("[^A-Za-z0-9._\\-]", "-");
        return s.isEmpty() ? "device" : s;
    }

    /** 计算设备模型相对路径（相对于模型存储根目录，不含 device-models 前缀） */
    private static String modelRelativePath(Scene3dConfig scene, String deviceCode, Integer iconType) {
        return scene.getProjectId() + "/" + scene.getId() + "/"
                + iconTypeLabel(iconType) + "/" + deviceCode + ".glb";
    }

    /**
     * 派生设备模型访问地址：文件存在则返回 /device-models/... 否则返回 null。
     * 设备编码优先取资产模块 device_code，缺失时降级为设备ID。
     */
    private String resolveModelUrl(Scene3dConfig scene, DeviceCoordinateMapping m) {
        if (scene == null || m == null || m.getDeviceId() == null) {
            return null;
        }
        DeviceVO dev = safeGetDevice(m.getDeviceId());
        String code = (dev != null && dev.getDeviceCode() != null && !dev.getDeviceCode().isBlank())
                ? sanitizeCode(dev.getDeviceCode()) : String.valueOf(m.getDeviceId());
        String relative = modelRelativePath(scene, code, m.getIconType());
        Path p = modelStorageProperties.resolve(relative);
        return Files.exists(p) ? "/device-models/" + relative : null;
    }

    // ======================== 场景配置 ========================

    @Override
    public SceneConfigVO getByProject(Long projectId) {
        Scene3dConfig cfg = configMapper.selectOne(
                new LambdaQueryWrapper<Scene3dConfig>().eq(Scene3dConfig::getProjectId, projectId));
        if (cfg == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "场景不存在");
        }
        return toConfigVO(cfg);
    }

    @Override
    public Long createConfig(SceneConfigCreateDTO dto) {
        assertAdmin();
        Scene3dConfig exist = configMapper.selectOne(
                new LambdaQueryWrapper<Scene3dConfig>().eq(Scene3dConfig::getProjectId, dto.getProjectId()));
        if (exist != null) {
            throw new SceneException(SystemCodeEnum.CONFLICT, "该项目已存在3D场景");
        }
        Scene3dConfig c = new Scene3dConfig();
        c.setProjectId(dto.getProjectId());
        c.setSceneName(dto.getSceneName());
        c.setCenterX(nullToZero(dto.getCenterX()));
        c.setCenterY(nullToZero(dto.getCenterY()));
        c.setCenterZ(nullToZero(dto.getCenterZ()));
        // 模型改为按设备分目录存放（访问地址由路径约定派生），场景级不再持有单一模型文件；
        // 简易几何体模式由管理员显式指定，缺省为简易模式。
        boolean simple = dto.getIsSimpleMode() != null ? dto.getIsSimpleMode() : true;
        c.setIsSimpleMode(simple ? 1 : 0);
        LocalDateTime now = LocalDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        configMapper.insert(c);
        return c.getId();
    }

    @Override
    public SceneConfigVO getConfigDetail(Long sceneId) {
        Scene3dConfig c = configMapper.selectById(sceneId);
        if (c == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "场景不存在");
        }
        return toConfigVO(c);
    }

    @Override
    public void updateConfig(Long sceneId, SceneConfigCreateDTO dto) {
        assertAdmin();
        Scene3dConfig c = configMapper.selectById(sceneId);
        if (c == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "场景不存在");
        }
        // projectId 不可改
        c.setSceneName(dto.getSceneName());
        if (dto.getCenterX() != null) {
            c.setCenterX(dto.getCenterX());
        }
        if (dto.getCenterY() != null) {
            c.setCenterY(dto.getCenterY());
        }
        if (dto.getCenterZ() != null) {
            c.setCenterZ(dto.getCenterZ());
        }
        if (dto.getIsSimpleMode() != null) {
            c.setIsSimpleMode(dto.getIsSimpleMode() ? 1 : 0);
        }
        c.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(c);
    }

    @Override
    public void deleteConfig(Long sceneId) {
        assertAdmin();
        Scene3dConfig c = configMapper.selectById(sceneId);
        if (c == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "场景不存在");
        }
        // 级联删除：先删关联的视频叠加，再删坐标映射，最后删场景
        for (DeviceCoordinateMapping coord : coordMapper.selectList(
                new LambdaQueryWrapper<DeviceCoordinateMapping>().eq(DeviceCoordinateMapping::getSceneId, sceneId))) {
            overlayMapper.delete(new LambdaQueryWrapper<SceneVideoOverlay>()
                    .eq(SceneVideoOverlay::getCoordinateId, coord.getId()));
        }
        coordMapper.delete(new LambdaQueryWrapper<DeviceCoordinateMapping>()
                .eq(DeviceCoordinateMapping::getSceneId, sceneId));
        configMapper.deleteById(sceneId);
    }

    @Override
    public ModelUploadResultVO uploadModel(Long sceneId, Long deviceId, Integer iconType, MultipartFile file) {
        assertAdmin();
        Scene3dConfig scene = configMapper.selectById(sceneId);
        if (scene == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "场景不存在");
        }
        if (deviceId == null) {
            throw new SceneException(SystemCodeEnum.BAD_REQUEST, "设备ID不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new SceneException(SystemCodeEnum.BAD_REQUEST, "模型文件不能为空");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_MODEL_EXT.contains(ext)) {
            throw new SceneException(SystemCodeEnum.BAD_REQUEST, "不支持的模型格式，仅支持 .glb（glTF 二进制）");
        }
        // 设备类型：优先用入参，否则取已有坐标的 iconType
        DeviceCoordinateMapping exist = coordMapper.selectOne(new LambdaQueryWrapper<DeviceCoordinateMapping>()
                .eq(DeviceCoordinateMapping::getSceneId, sceneId)
                .eq(DeviceCoordinateMapping::getDeviceId, deviceId));
        Integer type = iconType;
        if (type == null) {
            if (exist != null && exist.getIconType() != null) {
                type = exist.getIconType();
            } else {
                throw new SceneException(SystemCodeEnum.BAD_REQUEST, "缺少设备类型 iconType（首次上传该设备需传入）");
            }
        }
        // 设备编码（可读文件名）：来自资产模块，缺失时降级用设备ID
        DeviceVO device = safeGetDevice(deviceId);
        String deviceCode = (device != null && device.getDeviceCode() != null && !device.getDeviceCode().isBlank())
                ? sanitizeCode(device.getDeviceCode()) : String.valueOf(deviceId);
        // 存储路径：resources/device-models/{projectId}/{sceneId}/{iconTypeLabel}/{deviceCode}.glb（模型地址由约定派生，不落库）
        String relative = modelRelativePath(scene, deviceCode, type);
        Path target = modelStorageProperties.resolve(relative);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new SceneException(SystemCodeEnum.SYSTEM_ERROR, "创建模型目录失败：" + e.getMessage());
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new SceneException(SystemCodeEnum.SYSTEM_ERROR, "保存模型文件失败：" + e.getMessage());
        }
        String modelFileUrl = "/device-models/" + relative;
        String md5;
        try {
            md5 = DigestUtils.md5DigestAsHex(Files.newInputStream(target));
        } catch (IOException e) {
            throw new SceneException(SystemCodeEnum.SYSTEM_ERROR, "计算模型MD5失败：" + e.getMessage());
        }
        // 登记 / 更新设备坐标（把设备加入场景）；模型地址改为按路径约定派生，坐标表保持纯净只存坐标与类型
        DeviceCoordinateMapping m = exist;
        if (m == null) {
            m = new DeviceCoordinateMapping();
            m.setSceneId(sceneId);
            m.setDeviceId(deviceId);
            m.setPosX(BigDecimal.ZERO);
            m.setPosY(BigDecimal.ZERO);
            m.setPosZ(BigDecimal.ZERO);
            m.setRotationX(BigDecimal.ZERO);
            m.setRotationY(BigDecimal.ZERO);
            m.setRotationZ(BigDecimal.ZERO);
            m.setScaleX(BigDecimal.ONE);
            m.setScaleY(BigDecimal.ONE);
            m.setScaleZ(BigDecimal.ONE);
            m.setCreatedAt(LocalDateTime.now());
        }
        m.setIconType(type);
        m.setUpdatedAt(LocalDateTime.now());
        if (exist == null) {
            coordMapper.insert(m);
        } else {
            coordMapper.updateById(m);
        }
        ModelUploadResultVO vo = new ModelUploadResultVO();
        vo.setCoordinateId(m.getId());
        vo.setDeviceId(deviceId);
        vo.setModelFileUrl(modelFileUrl);
        vo.setModelFileMd5(md5);
        return vo;
    }

    // ======================== 设备坐标映射 ========================

    @Override
    public List<DeviceCoordinateVO> listDevices(Long sceneId, Integer iconType) {
        Scene3dConfig scene = configMapper.selectById(sceneId);
        LambdaQueryWrapper<DeviceCoordinateMapping> w = new LambdaQueryWrapper<DeviceCoordinateMapping>()
                .eq(DeviceCoordinateMapping::getSceneId, sceneId);
        if (iconType != null) {
            w.eq(DeviceCoordinateMapping::getIconType, iconType);
        }
        List<DeviceCoordinateMapping> list = coordMapper.selectList(w);
        List<DeviceCoordinateVO> vos = new ArrayList<>();
        for (DeviceCoordinateMapping m : list) {
            vos.add(toCoordinateVO(m, scene));
        }
        return vos;
    }

    @Override
    public Long createDeviceCoordinate(Long sceneId, DeviceCoordinateCreateDTO dto) {
        assertAdmin();
        Scene3dConfig scene = configMapper.selectById(sceneId);
        if (scene == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "场景不存在");
        }
        // TODO 依赖设备台账模块：校验「设备不存在」(404) 与「设备不属于当前项目」(403)，asset_device 尚未实现，暂跳过
        if (coordMapper.selectOne(new LambdaQueryWrapper<DeviceCoordinateMapping>()
                .eq(DeviceCoordinateMapping::getDeviceId, dto.getDeviceId())) != null) {
            throw new SceneException(SystemCodeEnum.CONFLICT, "该设备已在场景中标注，请勿重复添加");
        }
        DeviceCoordinateMapping m = new DeviceCoordinateMapping();
        m.setSceneId(sceneId);
        m.setDeviceId(dto.getDeviceId());
        m.setPosX(dto.getPosX());
        m.setPosY(dto.getPosY());
        m.setPosZ(dto.getPosZ());
        m.setRotationX(nullToZero(dto.getRotationX()));
        m.setRotationY(nullToZero(dto.getRotationY()));
        m.setRotationZ(nullToZero(dto.getRotationZ()));
        m.setScaleX(nullToOne(dto.getScaleX()));
        m.setScaleY(nullToOne(dto.getScaleY()));
        m.setScaleZ(nullToOne(dto.getScaleZ()));
        m.setIconType(dto.getIconType());
        LocalDateTime now = LocalDateTime.now();
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        coordMapper.insert(m);
        return m.getId();
    }

    @Override
    public DeviceDetailVO getDeviceDetail(Long sceneId, Long deviceId) {
        DeviceCoordinateMapping m = coordMapper.selectOne(new LambdaQueryWrapper<DeviceCoordinateMapping>()
                .eq(DeviceCoordinateMapping::getSceneId, sceneId)
                .eq(DeviceCoordinateMapping::getDeviceId, deviceId));
        if (m == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "该设备未在场景中标注");
        }
        DeviceDetailVO vo = new DeviceDetailVO();
        vo.setCoordinateId(m.getId());
        vo.setDeviceId(m.getDeviceId());
        // 设备编号/名称/在线状态来自资产模块（2004 已实现），通过 AssetDeviceService 获取
        DeviceVO device = safeGetDevice(m.getDeviceId());
        vo.setDeviceCode(device != null ? device.getDeviceCode() : null);
        vo.setDeviceName(device != null ? device.getDeviceName() : null);
        vo.setOnlineStatus(device != null && device.getOnlineStatus() != null ? device.getOnlineStatus() : 0);
        vo.setEnableStatus(device != null && device.getEnableStatus() != null ? device.getEnableStatus() : 0);
        // 最新告警级别来自告警模块（4002 临时只读接入，合并后改 AlarmMapper）
        vo.setLatestAlarmLevel(resolveLatestAlarmLevel(m.getDeviceId()));
        vo.setPosition(new Coordinate3DVO(m.getPosX(), m.getPosY(), m.getPosZ()));
        vo.setIconType(m.getIconType());
        vo.setModelFileUrl(resolveModelUrl(configMapper.selectById(sceneId), m));
        // 实时指标来自塔吊/升降机监控服务（3002 已推送），按 device_code 桥接
        vo.setRealtimeMetrics(resolveRealtimeMetrics(m.getDeviceId()));
        // 告警列表来自告警模块（4002 已推送），调用 AlarmService 查询未处置告警
        vo.setAlarms(resolveAlarmItems(m.getDeviceId()));
        vo.setDetailUrl("/devices/" + m.getDeviceId());
        return vo;
    }

    @Override
    public void updateDeviceCoordinate(Long coordinateId, DeviceCoordinateUpdateDTO dto) {
        assertAdmin();
        DeviceCoordinateMapping m = coordMapper.selectById(coordinateId);
        if (m == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "坐标映射不存在");
        }
        if (dto.getPosX() != null) {
            m.setPosX(dto.getPosX());
        }
        if (dto.getPosY() != null) {
            m.setPosY(dto.getPosY());
        }
        if (dto.getPosZ() != null) {
            m.setPosZ(dto.getPosZ());
        }
        if (dto.getRotationX() != null) {
            m.setRotationX(dto.getRotationX());
        }
        if (dto.getRotationY() != null) {
            m.setRotationY(dto.getRotationY());
        }
        if (dto.getRotationZ() != null) {
            m.setRotationZ(dto.getRotationZ());
        }
        if (dto.getScaleX() != null) {
            m.setScaleX(dto.getScaleX());
        }
        if (dto.getScaleY() != null) {
            m.setScaleY(dto.getScaleY());
        }
        if (dto.getScaleZ() != null) {
            m.setScaleZ(dto.getScaleZ());
        }
        if (dto.getIconType() != null) {
            m.setIconType(dto.getIconType());
        }
        m.setUpdatedAt(LocalDateTime.now());
        coordMapper.updateById(m);
    }

    @Override
    public void deleteDeviceCoordinate(Long coordinateId) {
        assertAdmin();
        DeviceCoordinateMapping m = coordMapper.selectById(coordinateId);
        if (m == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "坐标映射不存在");
        }
        // 级联删除关联的视频叠加
        overlayMapper.delete(new LambdaQueryWrapper<SceneVideoOverlay>()
                .eq(SceneVideoOverlay::getCoordinateId, coordinateId));
        coordMapper.deleteById(coordinateId);
    }

    @Override
    public List<AlarmDeviceVO> listAlarmDevices(Long sceneId) {
        // 告警模块（4002 已推送）：查该场景下处于未处置告警的设备，按最高级别高亮。
        // 逐设备调用 AlarmService.page 取未处置告警的最高级别（场景设备量不大，N 次查询可接受）。
        List<DeviceCoordinateMapping> mappings = coordMapper.selectList(
                new LambdaQueryWrapper<DeviceCoordinateMapping>().eq(DeviceCoordinateMapping::getSceneId, sceneId));
        List<AlarmDeviceVO> result = new ArrayList<>();
        for (DeviceCoordinateMapping m : mappings) {
            String lvl = resolveLatestAlarmLevel(m.getDeviceId());
            if (lvl == null) {
                continue;
            }
            AlarmDeviceVO vo = new AlarmDeviceVO();
            vo.setDeviceId(m.getDeviceId());
            DeviceVO dev = safeGetDevice(m.getDeviceId());
            vo.setDeviceName(dev != null ? dev.getDeviceName() : null);
            vo.setCoordinateId(m.getId());
            vo.setAlarmLevel(lvl);
            vo.setBlinkFrequency(1);
            vo.setPosition(new Coordinate3DVO(m.getPosX(), m.getPosY(), m.getPosZ()));
            result.add(vo);
        }
        return result;
    }

    // ======================== 视频叠加 ========================

    /**
     * 临时桥接取视频流地址（3005 未合并 develop 前）。
     * 坐标 → device_coordinate_mapping.device_id(bigint) → asset_device.id → asset_device.device_code → biz_camera.device_id(varchar) → stream_url。
     * ⚠️ 3005 合并后删除本方法，改用其 CameraMapper。
     */
    private String resolveCameraStreamUrl(Long coordinateId) {
        if (coordinateId == null) {
            return null;
        }
        DeviceCoordinateMapping coord = coordMapper.selectById(coordinateId);
        if (coord == null || coord.getDeviceId() == null) {
            return null;
        }
        DeviceVO device = safeGetDevice(coord.getDeviceId());
        if (device == null || device.getDeviceCode() == null) {
            return null;
        }
        try {
            return cameraReadMapper.selectStreamUrlByDeviceCode(device.getDeviceCode());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取设备最新未处置告警的最高级别（来自 4002 告警模块 AlarmService）。
     * 调用 AlarmService.page 按 deviceId + status=0(未处置) 查询；device_id 是 bigint，等于 asset_device.id，直接关联。
     * 返回 warning/alarm/control 或 null（无未处置告警）。跨模块查询异常时降级返回 null。
     */
    private String resolveLatestAlarmLevel(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        try {
            PageResult<AlarmListVO> page = alarmService.page(1, 200, null, null, null, 0, deviceId, null, null);
            Integer max = null;
            if (page != null && page.getRecords() != null) {
                for (AlarmListVO a : page.getRecords()) {
                    Integer lvl = a.getAlarmLevel();
                    if (lvl != null && (max == null || lvl > max)) {
                        max = lvl;
                    }
                }
            }
            return max == null ? null : alarmLevelToString(max);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取设备未处置告警列表（用于详情卡片 alarms，来自 4002 告警模块 AlarmService）。
     * 跨模块查询异常时降级为空列表，不影响 3D 场景展示。
     */
    private List<AlarmItemVO> resolveAlarmItems(Long deviceId) {
        List<AlarmItemVO> list = new ArrayList<>();
        if (deviceId == null) {
            return list;
        }
        try {
            PageResult<AlarmListVO> page = alarmService.page(1, 200, null, null, null, 0, deviceId, null, null);
            if (page != null && page.getRecords() != null) {
                for (AlarmListVO a : page.getRecords()) {
                    AlarmItemVO vo = new AlarmItemVO();
                    vo.setAlarmId(a.getId());
                    vo.setAlarmType(a.getAlarmTypeCode() != null ? a.getAlarmTypeCode()
                            : (a.getAlarmTypeText() != null ? a.getAlarmTypeText() : null));
                    Integer lvl = a.getAlarmLevel();
                    vo.setAlarmLevel(lvl == null ? null : alarmLevelToString(lvl));
                    vo.setAlarmTime(a.getAlarmTime() != null ? a.getAlarmTime().format(DT_FMT) : null);
                    list.add(vo);
                }
            }
        } catch (Exception e) {
            // 跨模块查询异常时降级为空列表，不影响 3D 场景展示
        }
        return list;
    }

    /**
     * 取设备实时指标（来自 3002 塔吊/升降机监控服务）。
     * 桥接路径：device_coordinate_mapping.device_id(bigint) → asset_device.id → asset_device.device_code
     * → TowerCraneService.latest / HoistService.latest（参数为设备编码 String）。
     * 每个设备仅属塔吊或升降机之一，未匹配的服务返回空列表自动跳过；跨模块异常降级为空列表。
     */
    private List<RealtimeMetricVO> resolveRealtimeMetrics(Long deviceId) {
        List<RealtimeMetricVO> list = new ArrayList<>();
        if (deviceId == null) {
            return list;
        }
        DeviceVO device = safeGetDevice(deviceId);
        if (device == null || device.getDeviceCode() == null) {
            return list;
        }
        String code = device.getDeviceCode();
        try {
            for (TowerCraneRealtimeVO v : towerCraneService.latest(code)) {
                addMetric(list, "起重量", v.getLoadWeight(), "kN", v.getAlarmStatus());
                addMetric(list, "力矩", v.getMoment(), "kN·m", v.getAlarmStatus());
                addMetric(list, "力矩百分比", v.getMomentPercent(), "%", v.getAlarmStatus());
                addMetric(list, "风速", v.getWindSpeed(), "m/s", v.getAlarmStatus());
                addMetric(list, "高度", v.getHeight(), "m", v.getAlarmStatus());
                addMetric(list, "幅度", v.getAmplitude(), "m", v.getAlarmStatus());
                addMetric(list, "回转角度", v.getSlewAngle(), "°", v.getAlarmStatus());
            }
        } catch (Exception e) {
            // 非塔吊设备或查询异常：跳过
        }
        try {
            for (HoistRealtimeVO v : hoistService.latest(code)) {
                addMetric(list, "载重", v.getLoadWeight(), "kN", v.getInterlockAlarm());
                addMetric(list, "载重百分比", v.getLoadPercent(), "%", v.getInterlockAlarm());
                addMetric(list, "风速", v.getWindSpeed(), "m/s", v.getInterlockAlarm());
                addMetric(list, "高度", v.getHeight(), "m", v.getInterlockAlarm());
                if (v.getPassengerCount() != null) {
                    addMetric(list, "载人数", BigDecimal.valueOf(v.getPassengerCount()), "人", v.getInterlockAlarm());
                }
            }
        } catch (Exception e) {
            // 非升降机设备或查询异常：跳过
        }
        return list;
    }

    private void addMetric(List<RealtimeMetricVO> list, String name, BigDecimal value, String unit, Integer alarmStatus) {
        if (value == null) {
            return;
        }
        RealtimeMetricVO m = new RealtimeMetricVO();
        m.setName(name);
        m.setValue(value);
        m.setUnit(unit);
        m.setIsAlarm(alarmStatus != null && alarmStatus > 0);
        list.add(m);
    }

    /** alarm_level 整数 → 字符串级别（排序 control>alarm>warning）。约定：>=3 control，==2 alarm，<=1 warning（待 4002 合并核对）。 */
    private static String alarmLevelToString(int level) {
        if (level >= 3) {
            return "control";
        }
        if (level == 2) {
            return "alarm";
        }
        return "warning";
    }

    @Override
    public List<VideoOverlayVO> listVideoOverlays(Long sceneId) {
        Set<Long> coordIds = sceneCoordinateIds(sceneId);
        if (coordIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SceneVideoOverlay> overlays = overlayMapper.selectList(
                new LambdaQueryWrapper<SceneVideoOverlay>().in(SceneVideoOverlay::getCoordinateId, coordIds));
        List<VideoOverlayVO> vos = new ArrayList<>();
        for (SceneVideoOverlay o : overlays) {
            VideoOverlayVO vo = new VideoOverlayVO();
            vo.setId(o.getId());
            vo.setCoordinateId(o.getCoordinateId());
            vo.setVideoWidth(o.getVideoWidth());
            vo.setVideoHeight(o.getVideoHeight());
            vo.setOffsetX(o.getOffsetX());
            vo.setOffsetY(o.getOffsetY());
            vo.setIsTransparent(isTransparent(o.getIsTransparent()));
            // 临时桥接取视频流：3005 摄像头模块未合并 develop，先用 SceneCameraReadMapper 只读 biz_camera。
            // device_id 是设备编码(String)，需经 device_coordinate_mapping.device_id(bigint) → asset_device → device_code 桥接。
            // ⚠️ 3005 合并后删除本调用，改用其 CameraMapper / BizCamera 实体。
            vo.setStreamUrl(resolveCameraStreamUrl(o.getCoordinateId()));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public Long createVideoOverlay(VideoOverlayCreateDTO dto) {
        assertAdmin();
        DeviceCoordinateMapping m = coordMapper.selectById(dto.getCoordinateId());
        if (m == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "该坐标不存在");
        }
        if (m.getIconType() == null || m.getIconType() != SceneConstants.ICON_CAMERA) {
            throw new SceneException(SystemCodeEnum.BAD_REQUEST, "该设备不是摄像头，无法叠加视频");
        }
        // 应用层控制：最多同时开启 4 路
        Set<Long> coordIds = sceneCoordinateIds(m.getSceneId());
        if (!coordIds.isEmpty()) {
            long count = overlayMapper.selectCount(
                    new LambdaQueryWrapper<SceneVideoOverlay>().in(SceneVideoOverlay::getCoordinateId, coordIds));
            if (count >= SceneConstants.MAX_VIDEO_OVERLAY) {
                throw new SceneException(SystemCodeEnum.BAD_REQUEST, "最多同时开启" + SceneConstants.MAX_VIDEO_OVERLAY + "路视频叠加");
            }
        }
        if (overlayMapper.selectOne(new LambdaQueryWrapper<SceneVideoOverlay>()
                .eq(SceneVideoOverlay::getCoordinateId, dto.getCoordinateId())) != null) {
            throw new SceneException(SystemCodeEnum.CONFLICT, "该坐标已配置视频叠加");
        }
        SceneVideoOverlay o = new SceneVideoOverlay();
        o.setCoordinateId(dto.getCoordinateId());
        o.setVideoWidth(dto.getVideoWidth() == null ? 320 : dto.getVideoWidth());
        o.setVideoHeight(dto.getVideoHeight() == null ? 240 : dto.getVideoHeight());
        o.setOffsetX(nullToZero(dto.getOffsetX()));
        o.setOffsetY(dto.getOffsetY() == null ? BigDecimal.valueOf(20) : dto.getOffsetY());
        o.setIsTransparent(dto.getIsTransparent() == null || dto.getIsTransparent() ? 1 : 0);
        LocalDateTime now = LocalDateTime.now();
        o.setCreatedAt(now);
        o.setUpdatedAt(now);
        overlayMapper.insert(o);
        return o.getId();
    }

    @Override
    public void updateVideoOverlay(Long overlayId, VideoOverlayUpdateDTO dto) {
        assertAdmin();
        SceneVideoOverlay o = overlayMapper.selectById(overlayId);
        if (o == null) {
            throw new SceneException(SystemCodeEnum.NOT_FOUND, "视频叠加配置不存在");
        }
        if (dto.getCoordinateId() != null) {
            o.setCoordinateId(dto.getCoordinateId());
        }
        if (dto.getVideoWidth() != null) {
            o.setVideoWidth(dto.getVideoWidth());
        }
        if (dto.getVideoHeight() != null) {
            o.setVideoHeight(dto.getVideoHeight());
        }
        if (dto.getOffsetX() != null) {
            o.setOffsetX(dto.getOffsetX());
        }
        if (dto.getOffsetY() != null) {
            o.setOffsetY(dto.getOffsetY());
        }
        if (dto.getIsTransparent() != null) {
            o.setIsTransparent(dto.getIsTransparent() ? 1 : 0);
        }
        o.setUpdatedAt(LocalDateTime.now());
        overlayMapper.updateById(o);
    }

    @Override
    public void deleteVideoOverlay(Long overlayId) {
        assertAdmin();
        overlayMapper.deleteById(overlayId);
    }

    // ======================== 私有转换 ========================

    private SceneConfigVO toConfigVO(Scene3dConfig c) {
        SceneConfigVO vo = new SceneConfigVO();
        vo.setId(c.getId());
        vo.setProjectId(c.getProjectId());
        vo.setSceneName(c.getSceneName());
        vo.setCenterX(c.getCenterX());
        vo.setCenterY(c.getCenterY());
        vo.setCenterZ(c.getCenterZ());
        vo.setIsSimpleMode(isSimple(c.getIsSimpleMode()));
        vo.setCreatedAt(fmt(c.getCreatedAt()));
        vo.setUpdatedAt(fmt(c.getUpdatedAt()));
        return vo;
    }

    private DeviceCoordinateVO toCoordinateVO(DeviceCoordinateMapping m, Scene3dConfig scene) {
        DeviceCoordinateVO vo = new DeviceCoordinateVO();
        vo.setCoordinateId(m.getId());
        vo.setSceneId(m.getSceneId());
        vo.setDeviceId(m.getDeviceId());
        // 设备编号/名称/在线状态来自资产模块（2004 已实现），通过 AssetDeviceService 获取
        DeviceVO device = safeGetDevice(m.getDeviceId());
        vo.setDeviceCode(device != null ? device.getDeviceCode() : null);
        vo.setDeviceName(device != null ? device.getDeviceName() : null);
        vo.setOnlineStatus(device != null && device.getOnlineStatus() != null ? device.getOnlineStatus() : 0);
        vo.setEnableStatus(device != null && device.getEnableStatus() != null ? device.getEnableStatus() : 0);
        // 最新告警级别来自告警模块（4002 临时只读接入，合并后改 AlarmMapper）
        vo.setLatestAlarmLevel(resolveLatestAlarmLevel(m.getDeviceId()));
        vo.setPosition(new Coordinate3DVO(m.getPosX(), m.getPosY(), m.getPosZ()));
        vo.setRotation(new Coordinate3DVO(m.getRotationX(), m.getRotationY(), m.getRotationZ()));
        vo.setScale(new Coordinate3DVO(m.getScaleX(), m.getScaleY(), m.getScaleZ()));
        vo.setIconType(m.getIconType());
        vo.setModelFileUrl(resolveModelUrl(scene, m));
        return vo;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal nullToOne(BigDecimal v) {
        return v == null ? BigDecimal.ONE : v;
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
