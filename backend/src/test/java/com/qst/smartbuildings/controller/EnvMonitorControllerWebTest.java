package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.EnvMonitorService;
import com.qst.smartbuildings.service.EnvMonitorPointService;
import com.qst.smartbuildings.vo.EnvDataPushResult;
import com.qst.smartbuildings.vo.EnvTrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnvMonitorController.class)
@Import(SecurityConfig.class)
class EnvMonitorControllerWebTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private EnvMonitorService service;
    @MockBean
    private EnvMonitorPointService pointService;
    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void reportAcceptsTimestampWithMilliseconds() throws Exception {
        when(service.report(any())).thenReturn(new EnvDataPushResult(10L, false, 0, "正常"));
        mockMvc.perform(post("/env-monitor/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitorPointId\":1,\"metricType\":\"PM25\",\"metricValue\":35.5,"
                                + "\"collectTime\":\"2026-08-05 14:30:25.123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(10));
        verify(service).report(any());
    }

    @Test
    void invalidHistoryTimeReturnsBadRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/env-monitor/history")
                        .param("monitorPointId", "1")
                        .param("metricType", "PM25")
                        .param("startTime", "bad")
                        .param("endTime", "2026-08-05 14:30:25"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendAcceptsDateTimeQueryValues() throws Exception {
        when(service.trend(anyLong(), any(), any(), anyString())).thenReturn(new EnvTrendVO());
        mockMvc.perform(get("/env-monitor/daily-stat/trend")
                        .param("monitorPointId", "2")
                        .param("startDate", "2026-09-29T00:00:00")
                        .param("endDate", "2026-09-29 23:59:59")
                        .param("metricType", "PM25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
