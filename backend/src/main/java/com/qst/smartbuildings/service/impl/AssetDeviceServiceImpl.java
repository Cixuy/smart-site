package com.qst.smartbuildings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.AssetDeviceType;
import com.qst.smartbuildings.domain.BizCamera;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.dto.DeviceAddDTO;
import com.qst.smartbuildings.dto.DeviceUpdateDTO;
import com.qst.smartbuildings.exception.AssetDeviceException;

import com.qst.smartbuildings.mapper.AssetDeviceLocationMapper;
import com.qst.smartbuildings.mapper.AssetDeviceMapper;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.AssetDeviceTypeMapper;
import com.qst.smartbuildings.mapper.BizCameraMapper;
import com.qst.smartbuildings.service.AssetDeviceService;
import com.qst.smartbuildings.vo.CertificateUploadResult;
import com.qst.smartbuildings.vo.DeviceOfflineLogVO;
import com.qst.smartbuildings.vo.DeviceOnlineRateResult;
import com.qst.smartbuildings.vo.DeviceSensorVO;
import com.qst.smartbuildings.vo.DeviceVO;
import com.qst.smartbuildings.vo.OnlineStatsResult;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.QrCodeResult;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备台账服务实现。
 */
@Service
public class AssetDeviceServiceImpl extends ServiceImpl<AssetDeviceMapper, Device> implements AssetDeviceService {

    @Autowired
    private AssetDeviceMapper assetDeviceMapper;

    @Autowired
    private AssetDeviceTypeMapper assetDeviceTypeMapper;

    @Autowired
    private AssetDeviceLocationMapper assetDeviceLocationMapper;

    @Autowired
    private BizCameraMapper bizCameraMapper;

    @Autowired
    private AssetDeviceSensorMapper assetDeviceSensorMapper;

    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(DeviceAddDTO dto) {
        // 校验设备类型存在且启用
        AssetDeviceType type = assetDeviceTypeMapper.selectById(dto.getTypeId());
        if (type == null || type.getIsDeleted() != null && type.getIsDeleted() == 1) {
            throw new AssetDeviceException("设备类型不存在或已删除");
        }
        // 校验安装位置存在且启用
        AssetDeviceLocation location = requireActiveLocation(dto.getLocationId());

        Device device = new Device();
        device.setDeviceName(dto.getDeviceName());
        device.setTypeId(dto.getTypeId());
        device.setLocationId(dto.getLocationId());
        device.setBrand(dto.getBrand());
        device.setModel(dto.getModel());
        device.setProductionDate(dto.getProductionDate());
        device.setSupplyDate(dto.getSupplyDate());
        device.setAcceptanceDate(dto.getAcceptanceDate());
        device.setInstallDate(dto.getInstallDate());
        device.setEnableDate(dto.getEnableDate());
        device.setExpectedScrapDate(dto.getExpectedScrapDate());
        device.setCertificateUrl(dto.getCertificateUrl());
        // 初始默认状态
        device.setOnlineStatus(0);
        device.setEnableStatus(1);
        device.setIsDeleted(0);
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());

        // 系统自动生成唯一设备编码：类型编码 + 时间戳 + 序号
        device.setDeviceCode(generateDeviceCode(type));

        assetDeviceMapper.insert(device);

        // 根据设备类型联动创建摄像头/传感器记录
        String typeCode = type.getTypeCode();
        if ("MONITOR".equals(typeCode)) {
            createLinkedCamera(device);
        } else if ("SENSOR".equals(typeCode)) {
            createLinkedSensor(device);
        }

        return device.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DeviceUpdateDTO dto) {
        Device existing = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, dto.getId())
                .eq(Device::getIsDeleted, 0));
        if (existing == null) {
            throw new AssetDeviceException("设备不存在或已删除");
        }

        // 若类型发生变更，校验新类型存在且未删除
        if (dto.getTypeId() != null) {
            AssetDeviceType type = assetDeviceTypeMapper.selectById(dto.getTypeId());
            if (type == null || type.getIsDeleted() != null && type.getIsDeleted() == 1) {
                throw new AssetDeviceException("设备类型不存在或已删除");
            }
        }
        // 若安装位置发生变更，校验新位置存在且未删除
        if (dto.getLocationId() != null) {
            requireActiveLocation(dto.getLocationId());
        }

        Device update = new Device();
        update.setId(dto.getId());
        update.setDeviceName(dto.getDeviceName());
        update.setTypeId(dto.getTypeId());
        update.setLocationId(dto.getLocationId());
        update.setBrand(dto.getBrand());
        update.setModel(dto.getModel());
        update.setProductionDate(dto.getProductionDate());
        update.setSupplyDate(dto.getSupplyDate());
        update.setAcceptanceDate(dto.getAcceptanceDate());
        update.setInstallDate(dto.getInstallDate());
        update.setEnableDate(dto.getEnableDate());
        update.setExpectedScrapDate(dto.getExpectedScrapDate());
        update.setActualScrapDate(dto.getActualScrapDate());
        update.setCertificateUrl(dto.getCertificateUrl());
        update.setUpdateTime(LocalDateTime.now());

        assetDeviceMapper.updateById(update);
        updateLinkedRecords(existing, update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevices(String ids) {
        if (!StringUtils.hasText(ids)) {
            throw new AssetDeviceException("设备ID不能为空");
        }
        List<Long> idList = new ArrayList<>();
        for (String part : ids.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(trimmed);
                if (id > 0) {
                    idList.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 忽略非法的ID片段
            }
        }
        if (idList.isEmpty()) {
            throw new AssetDeviceException("设备ID格式不正确");
        }
        // removeByIds 配合 @TableLogic 实现逻辑删除（is_deleted=1）
        List<Device> devices = assetDeviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .in(Device::getId, idList).eq(Device::getIsDeleted, 0));
        assetDeviceMapper.deleteBatchIds(idList);
        for (Device device : devices) {
            deleteLinkedRecords(device);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer enableStatus) {
        // 校验启用状态取值
        if (enableStatus == null || (enableStatus != 0 && enableStatus != 1)) {
            throw new AssetDeviceException("启用状态只能为0或1");
        }
        // 校验设备存在且未删除
        Device existing = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, id)
                .eq(Device::getIsDeleted, 0));
        if (existing == null) {
            throw new AssetDeviceException("设备不存在或已删除");
        }

        Device update = new Device();
        update.setId(id);
        update.setEnableStatus(enableStatus);
        update.setUpdateTime(LocalDateTime.now());
        assetDeviceMapper.updateById(update);
        updateLinkedRecords(existing, update);
    }

    @Override
    public QrCodeResult getQrCode(Long id) {
        Device device = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, id)
                .eq(Device::getIsDeleted, 0));
        if (device == null) {
            throw new AssetDeviceException("设备不存在或已删除");
        }
        QrCodeResult result = new QrCodeResult();
        result.setDeviceCode(device.getDeviceCode());
        result.setDeviceName(device.getDeviceName());
        result.setQrCodeUrl(device.getQrCodeUrl() != null ? device.getQrCodeUrl() : "");
        return result;
    }

    @Override
    public OnlineStatsResult getOnlineStats(Long locationId) {
        // 若指定位置，收集该节点及其所有子节点ID（支持按工地/楼栋/作业面统计）
        List<Long> locationIds = null;
        if (locationId != null) {
            locationIds = collectIds(locationId, id -> assetDeviceMapper.selectChildLocationIds(id));
        }

        // 基础条件：未删除，且按需限定位置范围
        long totalCount = countByStatus(locationIds, null, null);
        long onlineCount = countByStatus(locationIds, 1, null);
        long offlineCount = countByStatus(locationIds, 0, null);
        long disabledCount = countByStatus(locationIds, null, 0);

        return new OnlineStatsResult(totalCount, onlineCount, offlineCount, disabledCount);
    }

    @Override
    public DeviceVO getDeviceById(Long id) {
        // 1. 查询设备（未删除）
        Device device = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, id)
                .eq(Device::getIsDeleted, 0));
        if (device == null) {
            throw new AssetDeviceException("设备不存在或已删除");
        }

        // 2. 组装 DeviceVO
        DeviceVO vo = new DeviceVO();
        // 基础字段
        vo.setId(device.getId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceName(device.getDeviceName());
        vo.setTypeId(device.getTypeId());
        vo.setLocationId(device.getLocationId());
        vo.setBrand(device.getBrand());
        vo.setModel(device.getModel());
        vo.setProductionDate(device.getProductionDate());
        vo.setSupplyDate(device.getSupplyDate());
        vo.setAcceptanceDate(device.getAcceptanceDate());
        vo.setInstallDate(device.getInstallDate());
        vo.setEnableDate(device.getEnableDate());
        vo.setExpectedScrapDate(device.getExpectedScrapDate());
        vo.setActualScrapDate(device.getActualScrapDate());
        vo.setCertificateUrl(device.getCertificateUrl());
        vo.setQrCodeUrl(device.getQrCodeUrl());
        vo.setOnlineStatus(device.getOnlineStatus());
        vo.setEnableStatus(device.getEnableStatus());
        vo.setCreateTime(device.getCreateTime());
        //vo.setUpdateTime(device.getUpdateTime());

        // 3. 填充类型名称
        if (device.getTypeId() != null) {
            AssetDeviceType type = assetDeviceTypeMapper.selectById(device.getTypeId());
            if (type != null) {
                vo.setTypeName(type.getTypeName());
            }
        }

        // 4. 填充位置名称
        if (device.getLocationId() != null) {
            AssetDeviceLocation location = assetDeviceLocationMapper.selectById(device.getLocationId());
            if (location != null) {
                vo.setLocationName(location.getLocationName());
                // 如需完整路径（如 工地/楼栋/作业面），可额外查询父级拼接，此处按需扩展
            }
        }

        // 5. 查询并设置该设备的所有传感器（监测点）
        List<DeviceSensorVO> sensors = assetDeviceMapper.selectSensorsByDeviceIds(
                java.util.Collections.singletonList(id));
        vo.setSensors(sensors != null ? sensors : new ArrayList<>());

        return vo;
    }

    /**
     * 按状态统计设备数量。
     *
     * @param locationIds   位置ID集合（null 表示全局）
     * @param onlineStatus  在线状态过滤（可空）
     * @param enableStatus  启用状态过滤（可空）
     */
    private long countByStatus(List<Long> locationIds, Integer onlineStatus, Integer enableStatus) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
                .eq(Device::getIsDeleted, 0);
        if (locationIds != null) {
            wrapper.in(Device::getLocationId, locationIds);
        }
        if (onlineStatus != null) {
            wrapper.eq(Device::getOnlineStatus, onlineStatus);
        }
        if (enableStatus != null) {
            wrapper.eq(Device::getEnableStatus, enableStatus);
        }
        return assetDeviceMapper.selectCount(wrapper);
    }

    @Override
    public PageResult<DeviceVO> pageDevices(long pageNum, long pageSize, String deviceCode,






                                            String deviceName, Long typeId, Long locationId,
                                            Integer onlineStatus, Integer enableStatus, String brand) {
        // 空字符串统一归一化为 null
        deviceCode = blankToNull(deviceCode);
        deviceName = blankToNull(deviceName);
        brand = blankToNull(brand);

        // 如果指定了类型ID，收集该节点及其所有子节点ID（支持按塔吊等父级类型筛选所有下级设备）
        List<Long> typeIds = null;
        if (typeId != null) {
            typeIds = collectIds(typeId, id -> assetDeviceMapper.selectChildTypeIds(id));
        }

        // 如果指定了位置ID，收集该节点及其所有子节点ID（支持按工地/楼栋/作业面筛选）
        List<Long> locationIds = null;
        if (locationId != null) {
            locationIds = collectIds(locationId, id -> assetDeviceMapper.selectChildLocationIds(id));
        }

        Page<DeviceVO> page = new Page<>(pageNum, pageSize);
        IPage<DeviceVO> result = assetDeviceMapper.selectDevicePage(page, deviceCode, deviceName,
                typeIds, locationIds, onlineStatus, enableStatus, brand);

        // 批量填充每台设备的监测点
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            List<Long> deviceIds = result.getRecords().stream()
                    .map(DeviceVO::getId)
                    .collect(Collectors.toList());
            List<DeviceSensorVO> sensors = assetDeviceMapper.selectSensorsByDeviceIds(deviceIds);
            if (sensors != null) {
                Map<Long, List<DeviceSensorVO>> sensorMap = sensors.stream()
                        .collect(Collectors.groupingBy(DeviceSensorVO::getParentId));
                result.getRecords().forEach(vo -> vo.setSensors(sensorMap.get(vo.getId())));
            }
        }

        return new PageResult<>(result.getTotal(), result.getPages(), result.getCurrent(),
                result.getSize(), result.getRecords());
    }

    @Override
    public DeviceOnlineRateResult getDeviceOnlineRate(Long id, LocalDateTime beginTime, LocalDateTime endTime) {
        if (beginTime == null || endTime == null) {
            throw new AssetDeviceException("统计开始时间和结束时间不能为空");
        }
        if (!endTime.isAfter(beginTime)) {
            throw new AssetDeviceException("统计结束时间必须晚于开始时间");
        }
        Device device = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, id)
                .eq(Device::getIsDeleted, 0));
        if (device == null) {
            throw new AssetDeviceException("设备不存在或已删除");
        }

        long totalSeconds = ChronoUnit.SECONDS.between(beginTime, endTime);
        long offlineSeconds = 0;
        int offlineCount = 0;

        // 查询与统计周期重叠的离线日志（含仍未恢复的离线记录）
        List<DeviceOfflineLogVO> logs = assetDeviceMapper.selectOfflineLogsForDevice(id, beginTime, endTime);
        if (logs != null) {
            for (DeviceOfflineLogVO log : logs) {
                LocalDateTime offStart = log.getOfflineTime().isAfter(beginTime) ? log.getOfflineTime() : beginTime;
                LocalDateTime offEnd = (log.getOnlineTime() == null || log.getOnlineTime().isAfter(endTime))
                        ? endTime : log.getOnlineTime();
                if (offEnd.isAfter(offStart)) {
                    offlineSeconds += ChronoUnit.SECONDS.between(offStart, offEnd);
                    offlineCount++;
                }
            }
        }

        long onlineSeconds = Math.max(0, totalSeconds - offlineSeconds);
        return new DeviceOnlineRateResult(device.getId(), device.getDeviceName(), totalSeconds,
                onlineSeconds, offlineSeconds, offlineCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateUploadResult uploadCertificate(Long deviceId, MultipartFile file) {
        // 校验设备存在且未删除
        Device device = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, deviceId)
                .eq(Device::getIsDeleted, 0));
        if (device == null) {
            throw new AssetDeviceException("设备不存在或已删除");
        }
        // 校验文件非空
        if (file == null || file.isEmpty()) {
            throw new AssetDeviceException("上传文件不能为空");
        }
        // 校验文件大小（上限 10MB，与 multipart 配置一致）
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new AssetDeviceException("文件大小不能超过10MB");
        }
        // 校验扩展名（仅允许图片/PDF）
        String originalName = file.getOriginalFilename();
        String ext = StringUtils.hasText(originalName)
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf").contains(ext)) {
            throw new AssetDeviceException("仅支持上传图片或PDF格式的合格证附件");
        }

        // 生成唯一文件名：{时间戳}-{UUID}.{ext}
        String fileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // 保存到项目根目录同级的 certificate 文件夹（与 WebConfig /certificate/** 静态映射对应）
        String dirPath = System.getProperty("user.dir") + File.separator + "certificate" + File.separator;
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new AssetDeviceException("附件存储目录创建失败");
        }
        File dest = new File(dir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new AssetDeviceException("合格证附件保存失败");
        }

        // 数据库保存相对访问路径
        String relativePath = "/certificate/" + fileName;
        Device update = new Device();
        update.setId(device.getId());
        update.setCertificateUrl(relativePath);
        update.setUpdateTime(LocalDateTime.now());
        assetDeviceMapper.updateById(update);

        // 返回带上下文路径（/api）的完整访问地址
        String fileUrl = "/api" + relativePath;
        return new CertificateUploadResult(fileUrl);
    }

    /**
     * 设备类型为监控设备时，联动创建摄像头记录。
     */
    private void deleteLinkedRecords(Device device) {
        if (StringUtils.hasText(device.getDeviceCode())) {
            bizCameraMapper.update(null, new LambdaUpdateWrapper<BizCamera>()
                    .eq(BizCamera::getDeviceId, device.getDeviceCode())
                    .set(BizCamera::getIsDeleted, 1)
                    .set(BizCamera::getUpdatedAt, LocalDateTime.now()));
        }
        assetDeviceSensorMapper.update(null, new LambdaUpdateWrapper<AssetDeviceSensor>()
                .eq(AssetDeviceSensor::getParentId, device.getId())
                .eq(AssetDeviceSensor::getParentType, 1)
                .set(AssetDeviceSensor::getIsDeleted, 1)
                .set(AssetDeviceSensor::getStatus, 0));
        AssetDeviceType type = assetDeviceTypeMapper.selectById(device.getTypeId());
        if (type != null && "SENSOR".equals(type.getTypeCode())) {
            assetDeviceSensorMapper.update(null, new LambdaUpdateWrapper<AssetDeviceSensor>()
                    .eq(AssetDeviceSensor::getSensorName, device.getDeviceName())
                    .set(AssetDeviceSensor::getIsDeleted, 1)
                    .set(AssetDeviceSensor::getStatus, 0));
        }
    }

    private void updateLinkedRecords(Device existing, Device update) {
        if (StringUtils.hasText(existing.getDeviceCode())) {
            BizCamera cameraUpdate = new BizCamera();
            if (update.getDeviceName() != null) {
                cameraUpdate.setCameraName(update.getDeviceName());
            }
            if (update.getLocationId() != null) {
                cameraUpdate.setLocationId(update.getLocationId());
            }
            if (update.getOnlineStatus() != null) {
                cameraUpdate.setOnlineStatus(update.getOnlineStatus());
            }
            cameraUpdate.setUpdatedAt(LocalDateTime.now());
            bizCameraMapper.update(cameraUpdate, new LambdaQueryWrapper<BizCamera>()
                    .eq(BizCamera::getDeviceId, existing.getDeviceCode()));
        }
        AssetDeviceType type = assetDeviceTypeMapper.selectById(existing.getTypeId());
        if (type != null && "SENSOR".equals(type.getTypeCode()) && update.getDeviceName() != null) {
            AssetDeviceSensor sensorUpdate = new AssetDeviceSensor();
            sensorUpdate.setSensorName(update.getDeviceName());
            assetDeviceSensorMapper.update(sensorUpdate, new LambdaQueryWrapper<AssetDeviceSensor>()
                    .eq(AssetDeviceSensor::getSensorName, existing.getDeviceName()));
        }
    }
    private void createLinkedCamera(Device device) {
        BizCamera camera = new BizCamera();
        camera.setCameraName(device.getDeviceName());
        camera.setDeviceId(device.getDeviceCode());
        camera.setLocationId(device.getLocationId());
        camera.setStreamUrl("");
        camera.setProtocolType("RTSP");
        camera.setOnlineStatus(device.getOnlineStatus());
        camera.setIsDeleted(0);
        camera.setCreatedAt(LocalDateTime.now());
        camera.setUpdatedAt(LocalDateTime.now());
        bizCameraMapper.insert(camera);
    }

    /**
     * 设备类型为传感器设备时，联动创建传感器记录。
     */
    private void createLinkedSensor(Device device) {
        AssetDeviceSensor sensor = new AssetDeviceSensor();
        sensor.setSensorName(device.getDeviceName());
        sensor.setParentId(0L);
        sensor.setParentType(0);
        sensor.setMonitorType("GENERAL");
        sensor.setUnit("");
        sensor.setAlarmUpper(BigDecimal.ZERO);
        sensor.setRefreshRate(5);
        sensor.setStatus(1);
        sensor.setIsDeleted(0);
        assetDeviceSensorMapper.insert(sensor);
    }

    /**
     * 校验位置存在且未删除，并返回。
     */
    private AssetDeviceLocation requireActiveLocation(Long locationId) {

        AssetDeviceLocation location = assetDeviceLocationMapper.selectOne(new LambdaQueryWrapper<AssetDeviceLocation>()
                .eq(AssetDeviceLocation::getId, locationId)
                .eq(AssetDeviceLocation::getIsDeleted, 0));
        if (location == null) {
            throw new AssetDeviceException("安装位置不存在或已删除");
        }
        return location;
    }

    /**
     * 生成唯一设备编码，格式：{类型编码}-{yyyyMMddHHmmss}-{4位序号}。
     * 序号取该类型当前已有设备数量+1，并在写入前做唯一性兜底校验。
     */
    private String generateDeviceCode(AssetDeviceType type) {
        String prefix = StringUtils.hasText(type.getTypeCode()) ? type.getTypeCode() : "DEV";
        String timePart = LocalDateTime.now().format(CODE_TIME_FORMAT);
        long seq = assetDeviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getTypeId, type.getId())) + 1;
        while (true) {
            String code = String.format("%s-%s-%04d", prefix, timePart, seq);
            Long exists = assetDeviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                    .eq(Device::getDeviceCode, code));
            if (exists == 0) {
                return code;
            }
            seq++;
        }
    }

    /**
     * 将 blank 字符串转为 null。
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 收集指定节点及其所有子孙节点ID。
     *
     * @param rootId    根节点ID
     * @param childFunc 查询直接子节点ID的函数
     */
    private List<Long> collectIds(Long rootId, java.util.function.Function<Long, List<Long>> childFunc) {
        List<Long> ids = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        java.util.Stack<Long> stack = new java.util.Stack<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (cur == null || !visited.add(cur)) {
                continue;
            }
            ids.add(cur);
            List<Long> children = childFunc.apply(cur);
            if (children != null) {
                children.forEach(stack::push);
            }
        }
        return ids;
    }
}
