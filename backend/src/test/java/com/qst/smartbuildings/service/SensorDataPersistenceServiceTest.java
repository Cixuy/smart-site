package com.qst.smartbuildings.service;

import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.AssetDeviceType;
import com.qst.smartbuildings.domain.BizHoistRealtime;
import com.qst.smartbuildings.domain.BizTowerCraneRealtime;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.domain.EnvMonitorData;
import com.qst.smartbuildings.mapper.AssetDeviceMapper;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.AssetDeviceTypeMapper;
import com.qst.smartbuildings.mapper.BizHoistRealtimeMapper;
import com.qst.smartbuildings.mapper.BizTowerCraneRealtimeMapper;
import com.qst.smartbuildings.mapper.EnvMonitorDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDataPersistenceServiceTest {

    @Mock
    private AssetDeviceSensorMapper sensorMapper;
    @Mock
    private AssetDeviceMapper deviceMapper;
    @Mock
    private AssetDeviceTypeMapper deviceTypeMapper;
    @Mock
    private EnvMonitorDataMapper envDataMapper;
    @Mock
    private BizTowerCraneRealtimeMapper towerRealtimeMapper;
    @Mock
    private BizHoistRealtimeMapper hoistRealtimeMapper;
    @Mock
    private AlarmService alarmService;

    private SensorDataPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new SensorDataPersistenceService(sensorMapper, deviceMapper, deviceTypeMapper,
                envDataMapper, towerRealtimeMapper, hoistRealtimeMapper, alarmService, 1L);
    }

    @Test
    void persistsEnvDataWithAlarmStatus() {
        when(sensorMapper.selectById(1L)).thenReturn(envSensor(2, 100L, new BigDecimal("35")));
        service.persist(1L, "PM25", new BigDecimal("38"), "ug/m3", "2026-08-15 10:00:00.123");

        ArgumentCaptor<EnvMonitorData> captor = ArgumentCaptor.forClass(EnvMonitorData.class);
        verify(envDataMapper).insert(captor.capture());
        EnvMonitorData data = captor.getValue();
        assertThat(data.getMonitorPointId()).isEqualTo(100L);
        assertThat(data.getMetricType()).isEqualTo("PM25");
        assertThat(data.getMetricValue()).isEqualByComparingTo("38");
        assertThat(data.getUnit()).isEqualTo("ug/m3");
        assertThat(data.getStatus()).isEqualTo(2);
    }

    @Test
    void persistsTowerCraneDataWithAlarmStatus() {
        when(sensorMapper.selectById(7L)).thenReturn(deviceSensor(1, 11L, new BigDecimal("15")));
        when(deviceMapper.selectById(11L)).thenReturn(device("TC-001", 2L));
        when(deviceTypeMapper.selectById(2L)).thenReturn(type("塔吊", "CRANE"));

        service.persist(7L, "wind_speed", new BigDecimal("18"), "m/s", "2026-08-15 10:00:00.123");

        ArgumentCaptor<BizTowerCraneRealtime> captor = ArgumentCaptor.forClass(BizTowerCraneRealtime.class);
        verify(towerRealtimeMapper).insert(captor.capture());
        BizTowerCraneRealtime row = captor.getValue();
        assertThat(row.getDeviceId()).isEqualTo("TC-001");
        assertThat(row.getWindSpeed()).isEqualByComparingTo("18");
        assertThat(row.getAlarmStatus()).isEqualTo(2);
    }

    @Test
    void persistsHoistDataWithInterlockAlarm() {
        when(sensorMapper.selectById(8L)).thenReturn(deviceSensor(1, 9L, new BigDecimal("15")));
        when(deviceMapper.selectById(9L)).thenReturn(device("H-001", 8L));
        when(deviceTypeMapper.selectById(8L)).thenReturn(type("升降机", "ELEVATOR"));

        service.persist(8L, "load_weight", new BigDecimal("22"), "kN", "2026-08-15 10:00:00.123");

        ArgumentCaptor<BizHoistRealtime> captor = ArgumentCaptor.forClass(BizHoistRealtime.class);
        verify(hoistRealtimeMapper).insert(captor.capture());
        BizHoistRealtime row = captor.getValue();
        assertThat(row.getDeviceId()).isEqualTo("H-001");
        assertThat(row.getLoadWeight()).isEqualByComparingTo("22");
        assertThat(row.getInterlockAlarm()).isEqualTo(2);
    }

    @Test
    void skipsWhenSensorHasNoParent() {
        when(sensorMapper.selectById(1L)).thenReturn(envSensor(0, 100L, new BigDecimal("35")));
        service.persist(1L, "PM25", new BigDecimal("38"), "ug/m3", "2026-08-15 10:00:00.123");

        verify(envDataMapper, never()).insert(org.mockito.ArgumentMatchers.<EnvMonitorData>any());
        verify(towerRealtimeMapper, never()).insert(org.mockito.ArgumentMatchers.<BizTowerCraneRealtime>any());
        verify(hoistRealtimeMapper, never()).insert(org.mockito.ArgumentMatchers.<BizHoistRealtime>any());
    }

    @Test
    void triggersEnvAlarmWhenThresholdExceeded() {
        when(sensorMapper.selectById(1L)).thenReturn(envSensor(2, 100L, new BigDecimal("35")));
        service.persist(1L, "PM25", new BigDecimal("38"), "ug/m3", "2026-08-15 10:00:00.123");

        verify(alarmService).processEnvironmentAlarm(1L, null, 1L, "PM25",
                new BigDecimal("38"), 2, collectTime(), "预警上限=30, 报警上限=35");
    }

    @Test
    void triggersTowerAlarmWhenThresholdExceeded() {
        when(sensorMapper.selectById(7L)).thenReturn(deviceSensor(1, 11L, new BigDecimal("15")));
        when(deviceMapper.selectById(11L)).thenReturn(device("TC-001", 2L));
        when(deviceTypeMapper.selectById(2L)).thenReturn(type("塔吊", "CRANE"));

        service.persist(7L, "wind_speed", new BigDecimal("18"), "m/s", "2026-08-15 10:00:00.123");

        verify(alarmService).processDeviceAlarm(1L, 1L, 1L, "wind_speed",
                new BigDecimal("18"), 2, collectTime(),
                "预警上限=15, 报警上限=15", "TC-001：wind_speed监测值18超出阈值");
    }

    @Test
    void recoversAlarmWhenValueBackToNormal() {
        when(sensorMapper.selectById(7L)).thenReturn(deviceSensor(1, 11L, new BigDecimal("15")));
        when(deviceMapper.selectById(11L)).thenReturn(device("TC-001", 2L));
        when(deviceTypeMapper.selectById(2L)).thenReturn(type("塔吊", "CRANE"));

        service.persist(7L, "wind_speed", new BigDecimal("5"), "m/s", "2026-08-15 10:00:00.123");

        verify(alarmService).processDeviceAlarm(1L, 1L, 1L, "wind_speed",
                new BigDecimal("5"), 0, collectTime(),
                "预警上限=15, 报警上限=15", "TC-001：wind_speed监测值5超出阈值");
    }

    @Test
    void skipsAlarmWhenProjectIdUnset() {
        service = new SensorDataPersistenceService(sensorMapper, deviceMapper, deviceTypeMapper,
                envDataMapper, towerRealtimeMapper, hoistRealtimeMapper, alarmService, null);
        when(sensorMapper.selectById(1L)).thenReturn(envSensor(2, 100L, new BigDecimal("35")));
        service.persist(1L, "PM25", new BigDecimal("38"), "ug/m3", "2026-08-15 10:00:00.123");

        verify(alarmService, never()).processEnvironmentAlarm(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private AssetDeviceSensor envSensor(Integer parentType, Long parentId, BigDecimal alarmUpper) {
        AssetDeviceSensor s = baseSensor(parentType, parentId);
        s.setAlarmUpper(alarmUpper);
        s.setAlarmLower(new BigDecimal("0"));
        s.setWarningUpper(new BigDecimal("30"));
        s.setWarningLower(new BigDecimal("5"));
        return s;
    }

    private AssetDeviceSensor deviceSensor(Integer parentType, Long parentId, BigDecimal alarmUpper) {
        AssetDeviceSensor s = baseSensor(parentType, parentId);
        s.setAlarmUpper(alarmUpper);
        s.setWarningUpper(new BigDecimal("15"));
        return s;
    }

    private AssetDeviceSensor baseSensor(Integer parentType, Long parentId) {
        AssetDeviceSensor s = new AssetDeviceSensor();
        s.setId(1L);
        s.setParentId(parentId);
        s.setParentType(parentType);
        s.setUnit("unit");
        s.setIsDeleted(0);
        return s;
    }

    private Device device(String deviceCode, Long typeId) {
        Device d = new Device();
        d.setId(1L);
        d.setDeviceCode(deviceCode);
        d.setDeviceName(deviceCode);
        d.setTypeId(typeId);
        d.setIsDeleted(0);
        return d;
    }

    private AssetDeviceType type(String name, String code) {
        AssetDeviceType t = new AssetDeviceType();
        t.setTypeName(name);
        t.setTypeCode(code);
        return t;
    }

    private java.time.LocalDateTime collectTime() {
        return java.time.LocalDateTime.of(2026, 8, 15, 10, 0, 0, 123_000_000);
    }
}