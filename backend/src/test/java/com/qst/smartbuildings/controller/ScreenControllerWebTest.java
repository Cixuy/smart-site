package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.ScreenService;
import com.qst.smartbuildings.vo.DeviceStatusOverview;
import com.qst.smartbuildings.vo.EnvironmentMetric;
import com.qst.smartbuildings.vo.EquipmentRuntimeStatus;
import com.qst.smartbuildings.vo.MetricValue;
import com.qst.smartbuildings.vo.ScreenConfigListData;
import com.qst.smartbuildings.vo.ScreenRealtimeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScreenController.class)
@Import(SecurityConfig.class)
class ScreenControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScreenService screenService;

    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void listConfigsRequiresProjectIdAndReturnsRecordsWrapper() throws Exception {
        when(screenService.listConfigs(1001L, 1)).thenReturn(new ScreenConfigListData(List.of()));

        mockMvc.perform(get("/screen/config/list")
                        .param("projectId", "1001")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void createConfigReturnsIdResult() throws Exception {
        when(screenService.createConfig(any())).thenReturn(9L);

        mockMvc.perform(post("/screen/config/create")
                        .contentType("application/json")
                        .content("""
                                {
                                  "screenName":"综合安全态势大屏",
                                  "projectId":1001,
                                  "layoutConfig":{"gridCols":12,"rowHeight":80,"components":[]},
                                  "refreshInterval":5,
                                  "isDefault":true,
                                  "status":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    void realtimeSupportsOpenApiAndFrontendAlias() throws Exception {
        ScreenRealtimeData data = new ScreenRealtimeData();
        DeviceStatusOverview overview = new DeviceStatusOverview();
        overview.setOnlineCount(12);
        data.setDeviceStatus(overview);
        EnvironmentMetric environmentMetric = new EnvironmentMetric();
        environmentMetric.setMetric("PM25");
        environmentMetric.setValue(42.5d);
        environmentMetric.setUnit("ug/m3");
        environmentMetric.setExceeded(true);
        data.setEnvironmentMetrics(List.of(environmentMetric));
        EquipmentRuntimeStatus towerCraneStatus = new EquipmentRuntimeStatus();
        towerCraneStatus.setDeviceId(100L);
        towerCraneStatus.setDeviceName("1号塔吊");
        MetricValue metricValue = new MetricValue();
        metricValue.setName("风速");
        metricValue.setValue(6.5d);
        metricValue.setUnit("m/s");
        metricValue.setAlarm(true);
        towerCraneStatus.setMetrics(List.of(metricValue));
        data.setTowerCraneStatus(List.of(towerCraneStatus));
        when(screenService.getRealtimeData(eq(7L))).thenReturn(data);

        mockMvc.perform(get("/screen/realtime/get-data/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceStatus.onlineCount").value(12))
                .andExpect(jsonPath("$.data.environmentMetrics[0].isExceeded").value(true))
                .andExpect(jsonPath("$.data.environmentMetrics[0].exceeded").doesNotExist())
                .andExpect(jsonPath("$.data.towerCraneStatus[0].metrics[0].isAlarm").value(true))
                .andExpect(jsonPath("$.data.towerCraneStatus[0].metrics[0].alarm").doesNotExist());

        mockMvc.perform(get("/screen/realtime/data/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceStatus.onlineCount").value(12));
    }
}
