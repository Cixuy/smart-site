package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.dto.LocationAddDTO;
import com.qst.smartbuildings.dto.LocationUpdateDTO;
import com.qst.smartbuildings.exception.AssetLocationException;
import com.qst.smartbuildings.mapper.AssetDeviceLocationMapper;
import com.qst.smartbuildings.vo.LocationMapVO;
import com.qst.smartbuildings.vo.LocationTreeVO;
import com.qst.smartbuildings.vo.LocationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 设备位置管理服务
 */
@Service
public class AssetLocationService {

    private final AssetDeviceLocationMapper locationMapper;

    public AssetLocationService(AssetDeviceLocationMapper locationMapper) {
        this.locationMapper = locationMapper;
    }

    /**
     * 查询位置树
     *
     * @param status 状态筛选（可为空，查询全部）
     */
    public List<LocationTreeVO> tree(Integer status) {
        LambdaQueryWrapper<AssetDeviceLocation> wrapper = new LambdaQueryWrapper<AssetDeviceLocation>()
                .eq(AssetDeviceLocation::getIsDeleted, 0)
                .eq(status != null, AssetDeviceLocation::getStatus, status)
                .orderByAsc(AssetDeviceLocation::getSort, AssetDeviceLocation::getId);
        List<AssetDeviceLocation> all = locationMapper.selectList(wrapper);
        if (all.isEmpty()) {
            return new ArrayList<>();
        }
        // 构建 id -> 节点 映射
        Map<Long, LocationTreeVO> nodeMap = all.stream()
                .collect(Collectors.toMap(AssetDeviceLocation::getId, this::toTreeVO));
        List<LocationTreeVO> roots = new ArrayList<>();
        for (AssetDeviceLocation location : all) {
            LocationTreeVO vo = nodeMap.get(location.getId());
            if (location.getParentId() == null || location.getParentId() == 0L) {
                roots.add(vo);
            } else {
                LocationTreeVO parent = nodeMap.get(location.getParentId());
                if (parent == null) {
                    // 父节点不在本次结果中（可能被状态筛选过滤），提升为根节点
                    roots.add(vo);
                } else {
                    parent.getChildren().add(vo);
                }
            }
        }
        return roots;
    }

    /**
     * 查询位置列表（平铺）
     */
    public List<LocationVO> list(String locationName, Integer status, Long responsibleUserId) {
        return locationMapper.selectLocationList(locationName, status, responsibleUserId);
    }

    /**
     * 查询位置详情
     */
    public LocationVO detail(Long id) {
        LocationVO vo = locationMapper.selectLocationById(id);
        if (vo == null) {
            throw new AssetLocationException("位置不存在或已删除");
        }
        return vo;
    }

    /**
     * 新增位置
     */
    @Transactional(rollbackFor = Exception.class)
    public Long add(LocationAddDTO dto) {
        AssetDeviceLocation location = new AssetDeviceLocation();
        location.setLocationName(dto.getLocationName());

        // 父节点处理：默认根节点 level=1，否则校验父节点存在并计算层级
        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId == 0L) {
            location.setParentId(0L);
            location.setLevel(dto.getLevel() == null ? 1 : dto.getLevel());
        } else {
            AssetDeviceLocation parent = requireActiveLocation(parentId);
            location.setParentId(parentId);
            Integer parentLevel = parent.getLevel() == null ? 1 : parent.getLevel();
            location.setLevel(dto.getLevel() == null ? parentLevel + 1 : dto.getLevel());
        }

        location.setGisLon(dto.getGisLon());
        location.setGisLat(dto.getGisLat());
        location.setResponsibleUserId(dto.getResponsibleUserId());
        location.setSort(dto.getSort() == null ? 0 : dto.getSort());
        location.setStatus(1);
        location.setIsDeleted(0);
        locationMapper.insert(location);
        return location.getId();
    }

    /**
     * 修改位置
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(LocationUpdateDTO dto) {
        AssetDeviceLocation existing = requireActiveLocation(dto.getId());

        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new AssetLocationException("位置状态必须为0或1");
        }

        AssetDeviceLocation update = new AssetDeviceLocation();
        update.setId(existing.getId());
        update.setLocationName(dto.getLocationName());
        update.setGisLon(dto.getGisLon());
        update.setGisLat(dto.getGisLat());
        update.setResponsibleUserId(dto.getResponsibleUserId());
        update.setSort(dto.getSort());
        update.setStatus(dto.getStatus());
        locationMapper.updateById(update);
    }

    /**
     * 删除位置（批量，逻辑删除）
     * 存在子节点或已关联设备的位置不可删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new AssetLocationException("至少需要指定一个位置");
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new AssetLocationException("位置ID不能为空");
        }
        // 校验所有待删除位置均存在且未删除
        Long count = locationMapper.selectCount(new LambdaQueryWrapper<AssetDeviceLocation>()
                .in(AssetDeviceLocation::getId, distinctIds)
                .eq(AssetDeviceLocation::getIsDeleted, 0));
        if (count != distinctIds.size()) {
            throw new AssetLocationException("存在不存在或已删除的位置");
        }

        // 校验是否存在子节点
        Long childCount = locationMapper.selectCount(new LambdaQueryWrapper<AssetDeviceLocation>()
                .in(AssetDeviceLocation::getParentId, distinctIds)
                .eq(AssetDeviceLocation::getIsDeleted, 0));
        if (childCount > 0) {
            throw new AssetLocationException("存在子节点的位置不可删除，请先删除其子节点");
        }

        // 校验是否已关联设备（asset_device.location_id 指向本位置，且未删除）
        for (Long id : distinctIds) {
            Long deviceCount = locationMapper.countDeviceByLocationId(id);
            if (deviceCount > 0) {
                throw new AssetLocationException("位置【" + id + "】已关联设备，不可删除");
            }
        }

        // 逻辑删除
        locationMapper.update(null, new LambdaUpdateWrapper<AssetDeviceLocation>()
                .in(AssetDeviceLocation::getId, distinctIds)
                .set(AssetDeviceLocation::getIsDeleted, 1));
    }

    /**
     * 获取 GIS 坐标数据（所有启用位置及坐标）
     */
    public List<LocationMapVO> mapData() {
        return locationMapper.selectLocationMapData();
    }

    // ==================== 私有方法 ====================

    private AssetDeviceLocation requireActiveLocation(Long id) {
        AssetDeviceLocation location = locationMapper.selectOne(new LambdaQueryWrapper<AssetDeviceLocation>()
                .eq(AssetDeviceLocation::getId, id)
                .eq(AssetDeviceLocation::getIsDeleted, 0));
        if (location == null) {
            throw new AssetLocationException("位置不存在或已删除");
        }
        return location;
    }

    private LocationTreeVO toTreeVO(AssetDeviceLocation location) {
        LocationTreeVO vo = new LocationTreeVO();
        vo.setId(location.getId());
        vo.setLocationName(location.getLocationName());
        vo.setParentId(location.getParentId());
        vo.setLevel(location.getLevel());
        vo.setGisLon(location.getGisLon());
        vo.setGisLat(location.getGisLat());
        vo.setSort(location.getSort());
        return vo;
    }
}
