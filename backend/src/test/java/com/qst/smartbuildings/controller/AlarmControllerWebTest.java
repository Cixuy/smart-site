package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.exception.AlarmNotFoundException;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.AlarmService;
import com.qst.smartbuildings.service.UserService;
import com.qst.smartbuildings.vo.AlarmOverviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlarmController.class)
@Import(SecurityConfig.class)
class AlarmControllerWebTest {
    @Autowired MockMvc mockMvc;
    @MockBean AlarmService alarmService;
    @MockBean UserService userService;
    @MockBean AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void missingAlarmReturnsNotFound() throws Exception {
        when(alarmService.detail(99L)).thenThrow(new AlarmNotFoundException("告警不存在或已删除"));

        mockMvc.perform(get("/alarms/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("告警不存在或已删除"));
    }

    @Test
    void invalidIdentifierReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/alarms/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewAcceptsMilliseconds() throws Exception {
        when(alarmService.overview(any(), any(), any())).thenReturn(new AlarmOverviewVO());

        mockMvc.perform(get("/alarms/overview")
                        .param("projectId", "1")
                        .param("startTime", "2026-08-05T14:30:00.123")
                        .param("endTime", "2026-08-05T15:30:00.456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void invalidDateTimeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/alarms/overview")
                        .param("projectId", "1")
                        .param("startTime", "bad-time"))
                .andExpect(status().isBadRequest());
    }
}