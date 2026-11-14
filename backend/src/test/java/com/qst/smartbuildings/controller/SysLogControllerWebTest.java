package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.LogService;
import com.qst.smartbuildings.vo.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysLogController.class)
@Import(SecurityConfig.class)
class SysLogControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogService logService;

    @MockBean
    private AuthInterceptor authInterceptor;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void invalidPageParameterReturnsBadRequestInsteadOfAuthenticationError() throws Exception {
        mockMvc.perform(get("/sys/log/oper/page").param("pageNum", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("页码必须大于0"));
    }

    @Test
    void operDetailReturnsOk() throws Exception {
        com.qst.smartbuildings.vo.OperLogVO vo = new com.qst.smartbuildings.vo.OperLogVO();
        vo.setId(2L);
        vo.setTitle("设备台账");
        when(logService.getOperLogDetail(2L)).thenReturn(vo);

        mockMvc.perform(get("/sys/log/oper/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.title").value("设备台账"));
    }

    @Test
    void loginPageReturnsPageResult() throws Exception {
        when(logService.pageLoginLogs(1, 10, null, null, null, null, null))
                .thenReturn(new PageResult<>(0, 0, 1, 10, List.of()));

        mockMvc.perform(get("/sys/log/login/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }
}