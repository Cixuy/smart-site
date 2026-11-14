package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.SysOperLog;
import com.qst.smartbuildings.exception.LogException;
import com.qst.smartbuildings.mapper.SysLoginLogMapper;
import com.qst.smartbuildings.mapper.SysOperLogMapper;
import com.qst.smartbuildings.vo.OperLogVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {
    @Mock
    private SysLoginLogMapper loginLogMapper;
    @Mock
    private SysOperLogMapper operLogMapper;
    @InjectMocks
    private LogService service;

    @Test
    void getOperLogDetailReturnsDetailWhenExists() {
        SysOperLog entity = new SysOperLog();
        entity.setId(2L);
        entity.setTitle("设备台账");
        entity.setOperType("新增");
        entity.setOperByName("张三");
        entity.setOperById(1L);
        entity.setOperParam("{\"deviceName\":\"1号塔吊\"}");
        entity.setStatus(0);
        entity.setErrorMsg("");
        entity.setOperTime(LocalDateTime.of(2026, 8, 5, 10, 30));
        when(operLogMapper.selectById(2L)).thenReturn(entity);

        OperLogVO vo = service.getOperLogDetail(2L);

        assertThat(vo.getId()).isEqualTo(2L);
        assertThat(vo.getTitle()).isEqualTo("设备台账");
        assertThat(vo.getOperType()).isEqualTo("新增");
        assertThat(vo.getOperByName()).isEqualTo("张三");
        assertThat(vo.getOperById()).isEqualTo(1L);
        assertThat(vo.getOperParam()).contains("deviceName");
        assertThat(vo.getStatus()).isZero();
    }

    @Test
    void getOperLogDetailThrowsWhenMissing() {
        when(operLogMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getOperLogDetail(99L))
                .isInstanceOf(LogException.class)
                .hasMessage("操作日志不存在");
    }

    @Test
    void pageOperLogsParsesBeginTimeAndPaginates() {
        Page<SysOperLog> page = new Page<>(1, 10);
        page.setTotal(1);
        SysOperLog entity = new SysOperLog();
        entity.setId(3L);
        entity.setTitle("设备台账");
        entity.setStatus(0);
        page.setRecords(java.util.List.of(entity));
        when(operLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = service.pageOperLogs(1, 10, "设备台账", null, null, 0, "2026-08-05 00:00:00", "2026-08-06 00:00:00");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(3L);
    }

    @Test
    void pageOperLogsRejectsBeginAfterEnd() {
        assertThatThrownBy(() -> service.pageOperLogs(1, 10, null, null, null, null, "2026-08-06 00:00:00", "2026-08-05 00:00:00"))
                .isInstanceOf(LogException.class)
                .hasMessage("开始时间不能晚于结束时间");
    }

    @Test
    void pageOperLogsRejectsMalformedTime() {
        assertThatThrownBy(() -> service.pageOperLogs(1, 10, null, null, null, null, "20260805", null))
                .isInstanceOf(LogException.class);
    }
}