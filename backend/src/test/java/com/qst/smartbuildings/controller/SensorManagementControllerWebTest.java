package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.config.ModelStorageProperties;
import com.qst.smartbuildings.dto.SensorDataPushRequest;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.EnvMonitorService;
import com.qst.smartbuildings.service.SensorManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SensorManagementController.class)
@Import({SecurityConfig.class, SensorManagementControllerWebTest.TestConfig.class})
class SensorManagementControllerWebTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SensorManagementService sensorManagementService;
    @MockBean
    private EnvMonitorService envMonitorService;
    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void pushAcceptsCollectTimeWithoutIsoSeparator() throws Exception {
        mockMvc.perform(post("/asset/sensor/data/push")
                        .contentType("application/json")
                        .content("{\"sensorId\":8,\"metricValue\":111,\"collectTime\":\"2026-08-02 00:48:23\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<SensorDataPushRequest> captor = ArgumentCaptor.forClass(SensorDataPushRequest.class);
        verify(envMonitorService).pushSensorData(captor.capture());
        assertThat(captor.getValue().getCollectTime()).isEqualTo(LocalDateTime.of(2026, 8, 2, 0, 48, 23));
    }
    @TestConfiguration
    static class TestConfig {
        @Bean
        ModelStorageProperties modelStorageProperties() {
            return new ModelStorageProperties();
        }
    }
}