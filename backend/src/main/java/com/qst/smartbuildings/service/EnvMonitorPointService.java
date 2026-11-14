package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.EnvMonitorPoint;
import com.qst.smartbuildings.dto.EnvMonitorPointRequest;
import com.qst.smartbuildings.exception.EnvMonitorPointNotFoundException;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.mapper.EnvMonitorPointMapper;
import com.qst.smartbuildings.vo.EnvMonitorPointVO;
import com.qst.smartbuildings.vo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EnvMonitorPointService {
    private final EnvMonitorPointMapper mapper;

    public EnvMonitorPointService(EnvMonitorPointMapper mapper) { this.mapper = mapper; }

    public PageResult<EnvMonitorPointVO> page(long pageNum, long pageSize, String name, String location) {
        Page<EnvMonitorPoint> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<EnvMonitorPoint>()
                        .like(StringUtils.hasText(name), EnvMonitorPoint::getName, name == null ? null : name.trim())
                        .like(StringUtils.hasText(location), EnvMonitorPoint::getLocation, location == null ? null : location.trim())
                        .orderByDesc(EnvMonitorPoint::getId));
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toVo).toList());
    }

    public EnvMonitorPointVO detail(Long id) { return toVo(requirePoint(id)); }

    @Transactional(rollbackFor = Exception.class)
    public Long create(EnvMonitorPointRequest request) {
        EnvMonitorPoint point = new EnvMonitorPoint();
        point.setName(request.getName().trim());
        point.setLocation(trimToNull(request.getLocation()));
        mapper.insert(point);
        return point.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EnvMonitorPointRequest request) {
        requirePoint(id);
        EnvMonitorPoint point = new EnvMonitorPoint();
        point.setId(id);
        point.setName(request.getName().trim());
        point.setLocation(trimToNull(request.getLocation()));
        mapper.updateById(point);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requirePoint(id);
        if (mapper.countRunningSensors(id) > 0) {
            throw new MonitoringException("监测点存在运行中的传感器，不能删除");
        }
        mapper.deleteById(id);
    }

    private EnvMonitorPoint requirePoint(Long id) {
        EnvMonitorPoint point = id == null ? null : mapper.selectById(id);
        if (point == null) throw new EnvMonitorPointNotFoundException("环境监测点不存在");
        return point;
    }

    private EnvMonitorPointVO toVo(EnvMonitorPoint point) {
        EnvMonitorPointVO vo = new EnvMonitorPointVO();
        vo.setId(point.getId());
        vo.setName(point.getName());
        vo.setLocation(point.getLocation());
        var ids = mapper.selectSensorIds(point.getId());
        vo.setSensorIds(ids);
        vo.setSensorCount(ids.size());
        return vo;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}