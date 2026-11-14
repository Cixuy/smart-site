package com.qst.smartbuildings.service;

import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.vo.SensorMetaVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensorMetadataService {
    private final AssetDeviceSensorMapper sensorMapper;

    public SensorMetadataService(AssetDeviceSensorMapper sensorMapper) {
        this.sensorMapper = sensorMapper;
    }

    public List<SensorMetaVO> list(Long parentId, Integer parentType) {
        if (parentType != null && parentType != 1 && parentType != 2) {
            throw new MonitoringException("parentType 必须为 1（设备）或 2（监测点）");
        }
        return sensorMapper.selectSensorMeta(parentId, parentType);
    }
}