package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.ModelStorageProperties;
import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.SensorMetadataService;
import com.qst.smartbuildings.vo.SensorMetaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SensorMetadataController.class)
@Import({SecurityConfig.class, ModelStorageProperties.class})
class SensorMetadataControllerWebTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SensorMetadataService service;
    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void listReturnsSensorMeta() throws Exception {
        SensorMetaVO vo = new SensorMetaVO();
        vo.setId(1L);
        vo.setSensorName("塔吊1-风速");
        vo.setMonitorType("wind_speed");
        vo.setUnit("m/s");
        vo.setParentId(100L);
        vo.setParentType(1);
        vo.setWarningUpper(new BigDecimal("10"));
        vo.setWarningLower(new BigDecimal("0"));
        vo.setAlarmUpper(new BigDecimal("15"));
        vo.setRefreshRate(5);
        vo.setStatus(1);
        when(service.list(100L, 1)).thenReturn(List.of(vo));

        mockMvc.perform(get("/sensor/list").param("parentId", "100").param("parentType", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].sensorName").value("塔吊1-风速"))
                .andExpect(jsonPath("$.data[0].parentType").value(1))
                .andExpect(jsonPath("$.data[0].alarmUpper").value(15));
        verify(service).list(100L, 1);
    }

    @Test
    void listWithoutParamsReturnsAll() throws Exception {
        when(service.list(null, null)).thenReturn(List.of());
        mockMvc.perform(get("/sensor/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
        verify(service).list(null, null);
    }

    @Test
    void invalidParentTypeReturnsBadRequest() throws Exception {
        when(service.list(eq(1L), eq(9))).thenThrow(new MonitoringException("parentType 必须为 1（设备）或 2（监测点）"));
        mockMvc.perform(get("/sensor/list").param("parentId", "1").param("parentType", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}