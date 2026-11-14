package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qst.smartbuildings.domain.DeviceType;
import com.qst.smartbuildings.dto.CreateDeviceTypeRequest;
import com.qst.smartbuildings.dto.UpdateDeviceTypeRequest;
import com.qst.smartbuildings.exception.DeviceTypeException;
import com.qst.smartbuildings.mapper.DeviceTypeMapper;
import com.qst.smartbuildings.vo.DeviceTypeTreeVO;
import com.qst.smartbuildings.vo.DeviceTypeVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DeviceTypeService {
    private static final long ROOT_PARENT_ID = 0L;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 4;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final List<String> DEVICE_TABLE_CANDIDATES = List.of(
            "device", "asset_device", "equipment", "equipment_info", "asset_equipment");
    private static final List<String> TYPE_ID_COLUMN_CANDIDATES = List.of(
            "type_id", "device_type_id", "equipment_type_id");
    private static final List<String> LOGIC_DELETE_COLUMN_CANDIDATES = List.of(
            "is_deleted", "deleted");

    private final DeviceTypeMapper deviceTypeMapper;
    private final JdbcTemplate jdbcTemplate;

    public DeviceTypeService(DeviceTypeMapper deviceTypeMapper, JdbcTemplate jdbcTemplate) {
        this.deviceTypeMapper = deviceTypeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeviceTypeVO> list(String typeName, String typeCode, Integer status) {
        validateStatus(status);
        List<DeviceType> records = deviceTypeMapper.selectList(new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getIsDeleted, 0)
                .like(StringUtils.hasText(typeName), DeviceType::getTypeName, trim(typeName))
                .eq(StringUtils.hasText(typeCode), DeviceType::getTypeCode, trim(typeCode))
                .eq(status != null, DeviceType::getStatus, status)
                .orderByAsc(DeviceType::getSort)
                .orderByAsc(DeviceType::getId));
        return records.stream().map(this::toVo).toList();
    }

    public List<DeviceTypeTreeVO> tree(Integer status) {
        validateStatus(status);
        List<DeviceType> records = deviceTypeMapper.selectList(new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getIsDeleted, 0)
                .eq(status != null, DeviceType::getStatus, status)
                .orderByAsc(DeviceType::getSort)
                .orderByAsc(DeviceType::getId));

        Map<Long, DeviceTypeTreeVO> nodes = new HashMap<>();
        for (DeviceType record : records) {
            nodes.put(record.getId(), toTreeVo(record));
        }

        List<DeviceTypeTreeVO> roots = new ArrayList<>();
        for (DeviceTypeTreeVO node : nodes.values()) {
            Long parentId = normalizeParentId(node.getParentId());
            DeviceTypeTreeVO parent = nodes.get(parentId);
            if (parent == null || Objects.equals(parentId, ROOT_PARENT_ID)) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        Comparator<DeviceTypeTreeVO> comparator = Comparator
                .comparing(DeviceTypeTreeVO::getSort, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(DeviceTypeTreeVO::getId, Comparator.nullsFirst(Long::compareTo));
        sortTree(roots, comparator);
        return roots;
    }

    public DeviceTypeVO detail(Long id) {
        return toVo(requireActive(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateDeviceTypeRequest request) {
        String typeName = requiredText(request.getTypeName(), "类型名称不能为空");
        String typeCode = requiredText(request.getTypeCode(), "类型编码不能为空");
        Long parentId = normalizeParentId(request.getParentId());
        DeviceType parent = requireParent(parentId);
        int level = resolveLevel(request.getLevel(), parent);

        ensureUnique(typeCode, null);
        ensureNameUnique(typeName, parentId, null);

        DeviceType deviceType = new DeviceType();
        deviceType.setTypeName(typeName);
        deviceType.setTypeCode(typeCode);
        deviceType.setParentId(parentId);
        deviceType.setLevel(level);
        deviceType.setSort(request.getSort() == null ? 0 : request.getSort());
        deviceType.setStatus(ENABLED);
        deviceType.setRemark(trimToEmpty(request.getRemark()));
        deviceType.setIsDeleted(0);
        deviceType.setCreateTime(LocalDateTime.now());
        deviceType.setUpdateTime(LocalDateTime.now());
        deviceTypeMapper.insert(deviceType);
        return deviceType.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateDeviceTypeRequest request) {
        DeviceType existing = requireActive(request.getId());
        String typeName = request.getTypeName() == null
                ? existing.getTypeName()
                : requiredText(request.getTypeName(), "类型名称不能为空");
        String typeCode = request.getTypeCode() == null
                ? existing.getTypeCode()
                : requiredText(request.getTypeCode(), "类型编码不能为空");
        Long parentId = request.getParentId() == null
                ? normalizeParentId(existing.getParentId())
                : normalizeParentId(request.getParentId());
        DeviceType parent = requireParent(parentId);
        ensureNoCycle(existing.getId(), parentId);
        int level = resolveLevel(request.getLevel(), parent);

        ensureUnique(typeCode, existing.getId());
        ensureNameUnique(typeName, parentId, existing.getId());

        DeviceType update = new DeviceType();
        update.setId(existing.getId());
        update.setTypeName(typeName);
        update.setTypeCode(typeCode);
        update.setParentId(parentId);
        update.setLevel(level);
        update.setSort(request.getSort() == null ? existing.getSort() : request.getSort());
        update.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        update.setRemark(request.getRemark() == null ? existing.getRemark() : trimToEmpty(request.getRemark()));
        update.setUpdateTime(LocalDateTime.now());
        int affectedRows = deviceTypeMapper.updateById(update);
        if (affectedRows != 1) {
            throw new DeviceTypeException("设备类型更新失败，请刷新后重试");
        }

        if (!Objects.equals(existing.getLevel(), level) || !Objects.equals(existing.getParentId(), parentId)) {
            refreshDescendantLevels(existing.getId(), level, new HashSet<>());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Collection<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        List<DeviceType> records = deviceTypeMapper.selectList(new LambdaQueryWrapper<DeviceType>()
                .in(DeviceType::getId, normalizedIds)
                .eq(DeviceType::getIsDeleted, 0));
        if (records.size() != normalizedIds.size()) {
            throw new DeviceTypeException("存在不存在或已删除的设备类型");
        }

        long childCount = deviceTypeMapper.selectCount(new LambdaQueryWrapper<DeviceType>()
                .in(DeviceType::getParentId, normalizedIds)
                .eq(DeviceType::getIsDeleted, 0));
        if (childCount > 0) {
            throw new DeviceTypeException("存在子节点的设备类型不可删除");
        }

        if (countLinkedDevices(normalizedIds) > 0) {
            throw new DeviceTypeException("已关联设备的类型不可删除");
        }

        deviceTypeMapper.update(null, new LambdaUpdateWrapper<DeviceType>()
                .in(DeviceType::getId, normalizedIds)
                .set(DeviceType::getIsDeleted, 1)
                .set(DeviceType::getStatus, DISABLED)
                .set(DeviceType::getUpdateTime, LocalDateTime.now()));
    }

    private DeviceType requireActive(Long id) {
        if (id == null || id <= 0) {
            throw new DeviceTypeException("设备类型ID必须为正整数");
        }
        DeviceType deviceType = deviceTypeMapper.selectOne(new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getId, id)
                .eq(DeviceType::getIsDeleted, 0));
        if (deviceType == null) {
            throw new DeviceTypeException("设备类型不存在或已删除");
        }
        return deviceType;
    }

    private DeviceType requireParent(Long parentId) {
        if (Objects.equals(parentId, ROOT_PARENT_ID)) {
            return null;
        }
        DeviceType parent = requireActive(parentId);
        if (Objects.equals(parent.getStatus(), DISABLED)) {
            throw new DeviceTypeException("父节点已禁用，不能新增或移动子类型");
        }
        return parent;
    }

    private int resolveLevel(Integer requestedLevel, DeviceType parent) {
        int expectedLevel = parent == null ? MIN_LEVEL : safeLevel(parent) + 1;
        if (expectedLevel > MAX_LEVEL) {
            throw new DeviceTypeException("设备类型层级不能超过4级");
        }
        if (requestedLevel != null && requestedLevel != expectedLevel) {
            throw new DeviceTypeException("层级与父节点不匹配，应为第" + expectedLevel + "级");
        }
        return expectedLevel;
    }

    private int safeLevel(DeviceType deviceType) {
        return deviceType.getLevel() == null ? MIN_LEVEL : deviceType.getLevel();
    }

    private void ensureUnique(String typeCode, Long excludedId) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getTypeCode, typeCode)
                .eq(DeviceType::getIsDeleted, 0);
        if (excludedId != null) {
            wrapper.ne(DeviceType::getId, excludedId);
        }
        if (deviceTypeMapper.selectCount(wrapper) > 0) {
            throw new DeviceTypeException("类型编码已存在");
        }
    }

    private void ensureNameUnique(String typeName, Long parentId, Long excludedId) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getTypeName, typeName)
                .eq(DeviceType::getParentId, parentId)
                .eq(DeviceType::getIsDeleted, 0);
        if (excludedId != null) {
            wrapper.ne(DeviceType::getId, excludedId);
        }
        if (deviceTypeMapper.selectCount(wrapper) > 0) {
            throw new DeviceTypeException("同一父节点下类型名称已存在");
        }
    }

    private void ensureNoCycle(Long id, Long parentId) {
        if (Objects.equals(id, parentId)) {
            throw new DeviceTypeException("父节点不能是自身");
        }
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (!Objects.equals(currentId, ROOT_PARENT_ID)) {
            if (!visited.add(currentId)) {
                throw new DeviceTypeException("设备类型树存在循环引用");
            }
            DeviceType current = requireActive(currentId);
            if (Objects.equals(current.getId(), id)) {
                throw new DeviceTypeException("父节点不能是当前节点的子节点");
            }
            currentId = normalizeParentId(current.getParentId());
        }
    }

    private void refreshDescendantLevels(Long parentId, int parentLevel, Set<Long> visited) {
        if (!visited.add(parentId)) {
            throw new DeviceTypeException("设备类型树存在循环引用");
        }
        List<DeviceType> children = deviceTypeMapper.selectList(new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getParentId, parentId)
                .eq(DeviceType::getIsDeleted, 0)
                .orderByAsc(DeviceType::getSort)
                .orderByAsc(DeviceType::getId));
        for (DeviceType child : children) {
            int childLevel = parentLevel + 1;
            if (childLevel > MAX_LEVEL) {
                throw new DeviceTypeException("设备类型层级不能超过4级");
            }
            if (!Objects.equals(child.getLevel(), childLevel)) {
                DeviceType update = new DeviceType();
                update.setId(child.getId());
                update.setLevel(childLevel);
                update.setUpdateTime(LocalDateTime.now());
                deviceTypeMapper.updateById(update);
            }
            refreshDescendantLevels(child.getId(), childLevel, visited);
        }
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null) {
            throw new DeviceTypeException("至少需要指定一个设备类型");
        }
        List<Long> result = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (result.isEmpty()) {
            throw new DeviceTypeException("设备类型ID不能为空");
        }
        return result;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != ENABLED && status != DISABLED) {
            throw new DeviceTypeException("状态必须为0或1");
        }
    }

    private long countLinkedDevices(List<Long> typeIds) {
        DeviceLinkTable linkTable = findDeviceLinkTable();
        if (linkTable == null) {
            return 0L;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(typeIds.size(), "?"));
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM ")
                .append(quoteIdentifier(linkTable.tableName()))
                .append(" WHERE ")
                .append(quoteIdentifier(linkTable.typeIdColumn()))
                .append(" IN (")
                .append(placeholders)
                .append(")");
        if (linkTable.logicDeleteColumn() != null) {
            sql.append(" AND ")
                    .append(quoteIdentifier(linkTable.logicDeleteColumn()))
                    .append(" = 0");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, typeIds.toArray());
        return count == null ? 0L : count;
    }

    private DeviceLinkTable findDeviceLinkTable() {
        return jdbcTemplate.execute((Connection connection) -> {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String tableName : DEVICE_TABLE_CANDIDATES) {
                if (!tableExists(metaData, catalog, tableName)) {
                    continue;
                }
                String typeIdColumn = firstExistingColumn(metaData, catalog, tableName, TYPE_ID_COLUMN_CANDIDATES);
                if (typeIdColumn == null) {
                    continue;
                }
                String logicDeleteColumn = firstExistingColumn(metaData, catalog, tableName, LOGIC_DELETE_COLUMN_CANDIDATES);
                return new DeviceLinkTable(tableName, typeIdColumn, logicDeleteColumn);
            }
            return null;
        });
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private String firstExistingColumn(DatabaseMetaData metaData, String catalog, String tableName, List<String> columns)
            throws SQLException {
        for (String column : columns) {
            try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, column)) {
                if (resultSet.next()) {
                    return column;
                }
            }
        }
        return null;
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            throw new DeviceTypeException(message);
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        String normalized = trim(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String trimToEmpty(String value) {
        String normalized = trim(value);
        return normalized == null ? "" : normalized;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private void sortTree(List<DeviceTypeTreeVO> nodes, Comparator<DeviceTypeTreeVO> comparator) {
        nodes.sort(comparator);
        for (DeviceTypeTreeVO node : nodes) {
            sortTree(node.getChildren(), comparator);
        }
    }

    private DeviceTypeVO toVo(DeviceType deviceType) {
        DeviceTypeVO result = new DeviceTypeVO();
        result.setId(deviceType.getId());
        result.setTypeName(safeString(deviceType.getTypeName()));
        result.setTypeCode(safeString(deviceType.getTypeCode()));
        result.setParentId(normalizeParentId(deviceType.getParentId()));
        result.setLevel(deviceType.getLevel());
        result.setSort(deviceType.getSort());
        result.setStatus(deviceType.getStatus());
        result.setRemark(safeString(deviceType.getRemark()));
        result.setCreateTime(deviceType.getCreateTime());
        return result;
    }

    private DeviceTypeTreeVO toTreeVo(DeviceType deviceType) {
        DeviceTypeTreeVO result = new DeviceTypeTreeVO();
        result.setId(deviceType.getId());
        result.setTypeName(safeString(deviceType.getTypeName()));
        result.setTypeCode(safeString(deviceType.getTypeCode()));
        result.setParentId(normalizeParentId(deviceType.getParentId()));
        result.setLevel(deviceType.getLevel());
        result.setSort(deviceType.getSort());
        result.setStatus(deviceType.getStatus());
        result.setRemark(safeString(deviceType.getRemark()));
        return result;
    }

    private record DeviceLinkTable(String tableName, String typeIdColumn, String logicDeleteColumn) {
    }
}
