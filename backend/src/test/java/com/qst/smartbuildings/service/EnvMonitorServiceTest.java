package com.qst.smartbuildings.service;

import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.EnvMonitorData;
import com.qst.smartbuildings.dto.SensorDataPushRequest;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.EnvMonitorDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvMonitorServiceTest {
    @Mock
    private EnvMonitorDataMapper dataMapper;
    @Mock
    private AssetDeviceSensorMapper sensorMapper;
    @InjectMocks
    private EnvMonitorService service;

    @Test
    void pushPersistsOnlyColumnsDefinedByEnvMonitorDataTable() {
        AssetDeviceSensor sensor = new AssetDeviceSensor();
        sensor.setId(2L);
        sensor.setParentId(11L);
        sensor.setMonitorType("PM25");
        sensor.setUnit("ug/m3");
        sensor.setWarningUpper(new BigDecimal("89"));
        sensor.setAlarmUpper(new BigDecimal("100"));
        sensor.setIsDeleted(0);
        when(sensorMapper.selectOne(any())).thenReturn(sensor);

        SensorDataPushRequest request = new SensorDataPushRequest();
        request.setSensorId(2L);
        request.setMetricValue(new BigDecimal("6"));
        request.setCollectTime(LocalDateTime.of(2026, 9, 29, 9, 41, 14));

        service.pushSensorData(request);

        ArgumentCaptor<EnvMonitorData> captor = ArgumentCaptor.forClass(EnvMonitorData.class);
        verify(dataMapper).insert(captor.capture());
        EnvMonitorData data = captor.getValue();
        assertThat(data.getMonitorPointId()).isEqualTo(11L);
        assertThat(data.getDeviceId()).isNull();
        assertThat(data.getMetricType()).isEqualTo("PM25");
        assertThat(data.getMetricValue()).isEqualByComparingTo("6");
        assertThat(data.getCollectTime()).isEqualTo(LocalDateTime.of(2026, 9, 29, 9, 41, 14));
        assertThat(data.getStatus()).isZero();
    }

    @Test
    void pushRejectsDuplicateMonitorPointMetricAndCollectTime() {
        AssetDeviceSensor sensor = new AssetDeviceSensor();
        sensor.setId(2L);
        sensor.setParentId(11L);
        sensor.setMonitorType("PM25");
        sensor.setUnit("ug/m3");
        sensor.setIsDeleted(0);
        when(sensorMapper.selectOne(any())).thenReturn(sensor);
        when(dataMapper.insert(any(EnvMonitorData.class)))
                .thenThrow(new DuplicateKeyException("uk_point_metric_collect"));

        SensorDataPushRequest request = new SensorDataPushRequest();
        request.setSensorId(2L);
        request.setMetricValue(new BigDecimal("35.5"));
        request.setCollectTime(LocalDateTime.of(2026, 8, 5, 14, 30, 25, 123_000_000));

        assertThatThrownBy(() -> service.pushSensorData(request))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("数据已存在");
    }
}
