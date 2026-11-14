package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.dto.SensorCreateRequest;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorManagementServiceTest {
    @Mock
    private AssetDeviceSensorMapper sensorMapper;
    @InjectMocks
    private SensorManagementService service;

    @Test
    void createUsesRunningStatusAndDefaultRefreshRate() {
        when(sensorMapper.insert(any(AssetDeviceSensor.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AssetDeviceSensor.class).setId(7L);
            return 1;
        });
        SensorCreateRequest request = new SensorCreateRequest();
        request.setSensorName("东门PM25");
        request.setParentId(11L);
        request.setParentType(0);
        request.setMonitorType("PM25");
        request.setUnit("ug/m3");
        request.setAlarmUpper(new BigDecimal("75"));

        assertThat(service.create(request)).isEqualTo(7L);
    }

    @Test
    void createRejectsUndefinedMonitorType() {
        SensorCreateRequest request = new SensorCreateRequest();
        request.setSensorName("无效监测点");
        request.setParentId(11L);
        request.setParentType(0);
        request.setMonitorType("eu ");
        request.setUnit("m/s");
        request.setAlarmUpper(new BigDecimal("75"));

        assertThatThrownBy(() -> service.create(request))
                .hasMessage("监测类型不支持，请使用已定义的指标类型");
    }

    @Test
    void createTrimsSupportedMonitorType() {
        when(sensorMapper.insert(any(AssetDeviceSensor.class))).thenAnswer(invocation -> {
            AssetDeviceSensor sensor = invocation.getArgument(0, AssetDeviceSensor.class);
            sensor.setId(8L);
            assertThat(sensor.getMonitorType()).isEqualTo("WIND");
            return 1;
        });
        SensorCreateRequest request = new SensorCreateRequest();
        request.setSensorName(" 东门风速 ");
        request.setParentId(11L);
        request.setParentType(0);
        request.setMonitorType(" WIND ");
        request.setUnit(" m/s ");
        request.setAlarmUpper(new BigDecimal("20"));

        assertThat(service.create(request)).isEqualTo(8L);
    }
}