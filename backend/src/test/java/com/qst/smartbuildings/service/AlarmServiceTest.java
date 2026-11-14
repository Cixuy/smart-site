package com.qst.smartbuildings.service;

import com.qst.smartbuildings.domain.AlarmDisposal;
import com.qst.smartbuildings.domain.AlarmRecord;
import com.qst.smartbuildings.domain.AlarmRecovery;
import com.qst.smartbuildings.dto.AlarmCreateDTO;
import com.qst.smartbuildings.dto.AlarmDisposalDTO;
import com.qst.smartbuildings.dto.AlarmRecoveryDTO;
import com.qst.smartbuildings.exception.AlarmException;
import com.qst.smartbuildings.exception.AlarmNotFoundException;
import com.qst.smartbuildings.mapper.AiScreenshotMapper;
import com.qst.smartbuildings.mapper.AlarmDailyStatMapper;
import com.qst.smartbuildings.mapper.AlarmDisposalMapper;
import com.qst.smartbuildings.mapper.AlarmPushLogMapper;
import com.qst.smartbuildings.mapper.AlarmRecoveryMapper;
import com.qst.smartbuildings.mapper.AlarmRecordMapper;
import com.qst.smartbuildings.vo.AlarmListVO;
import com.qst.smartbuildings.vo.DisposalRecordVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {
    @Mock AlarmRecordMapper alarmRecordMapper;
    @Mock AlarmDisposalMapper disposalMapper;
    @Mock AlarmRecoveryMapper recoveryMapper;
    @Mock AlarmDailyStatMapper dailyStatMapper;
    @Mock AlarmPushLogMapper pushLogMapper;
    @Mock AiScreenshotMapper screenshotMapper;
    @InjectMocks AlarmService alarmService;

    @Test
    void rejectsFalseAlarmWithoutReason() {
        AlarmDisposalDTO dto = disposalRequest(2);
        assertThrows(AlarmException.class, () -> alarmService.dispose(dto, 1L, "管理员"));
        verify(alarmRecordMapper, never()).selectById(any());
    }

    @Test
    void disposalWritesHistoryAndResolvesAlarm() {
        AlarmRecord record = alarmRecord(10L);
        when(alarmRecordMapper.selectById(10L)).thenReturn(record);
        when(disposalMapper.insert(any(AlarmDisposal.class))).thenReturn(1);
        when(alarmRecordMapper.updateStatus(10L, 2)).thenReturn(1);

        alarmService.dispose(disposalRequest(1), 1L, "管理员");

        verify(disposalMapper).insert(any(AlarmDisposal.class));
        verify(alarmRecordMapper).updateStatus(10L, 2);
    }

    @Test
    void detailMissingUsesNotFoundError() {
        when(alarmRecordMapper.selectDetail(99L)).thenReturn(null);
        assertThrows(AlarmNotFoundException.class, () -> alarmService.detail(99L));
    }

    @Test
    void logicalDeleteUpdatesOnlyExistingAlarm() {
        AlarmListVO detail = new AlarmListVO();
        detail.setId(10L);
        detail.setProjectId(1L);
        detail.setAlarmTime(LocalDateTime.of(2026, 8, 5, 14, 30));
        when(alarmRecordMapper.selectDetail(10L)).thenReturn(detail);
        when(alarmRecordMapper.logicalDelete(10L)).thenReturn(1);

        alarmService.logicalDelete(10L);

        verify(alarmRecordMapper).logicalDelete(10L);
    }

    @Test
    void recoveryIsIdempotent() {
        AlarmRecord record = alarmRecord(10L);
        when(alarmRecordMapper.selectById(10L)).thenReturn(record);
        when(recoveryMapper.selectIdByAlarmId(10L)).thenReturn(20L);
        AlarmRecoveryDTO dto = new AlarmRecoveryDTO();
        dto.setAlarmId(10L);

        alarmService.recover(dto);

        verify(recoveryMapper, never()).insert(any(AlarmRecovery.class));
    }

    @Test
    void recoveryPreservesMillisecondTimestamp() {
        AlarmRecord record = alarmRecord(10L);
        LocalDateTime recoveryTime = LocalDateTime.of(2026, 8, 5, 14, 45, 0, 123_000_000);
        when(alarmRecordMapper.selectById(10L)).thenReturn(record);
        when(recoveryMapper.selectIdByAlarmId(10L)).thenReturn(null);
        when(recoveryMapper.insert(any(AlarmRecovery.class))).thenReturn(1);
        AlarmRecoveryDTO dto = new AlarmRecoveryDTO();
        dto.setAlarmId(10L);
        dto.setRecoveryTime(recoveryTime);

        alarmService.recover(dto);

        ArgumentCaptor<AlarmRecovery> captor = ArgumentCaptor.forClass(AlarmRecovery.class);
        verify(recoveryMapper).insert(captor.capture());
        assertEquals(recoveryTime, captor.getValue().getRecoveryTime());
    }

    @Test
    void disposalPageChecksAlarmAndPaginates() {
        AlarmListVO detail = new AlarmListVO();
        detail.setId(10L);
        detail.setProjectId(1L);
        detail.setAlarmTime(LocalDateTime.of(2026, 8, 5, 14, 30));
        when(alarmRecordMapper.selectDetail(10L)).thenReturn(detail);
        when(disposalMapper.countPage(10L)).thenReturn(21L);
        when(disposalMapper.selectPage(10L, 20L, 20L)).thenReturn(List.of(new DisposalRecordVO()));

        var page = alarmService.disposals(10L, 2, 20);

        assertEquals(21L, page.getTotal());
        assertEquals(2L, page.getPages());
        verify(disposalMapper).selectPage(10L, 20L, 20L);
    }

    @Test
    void retryOnlyResetsFailedPushLog() {
        when(pushLogMapper.resetFailedForRetry(30L)).thenReturn(1);
        alarmService.retryPush(30L);
        verify(pushLogMapper).resetFailedForRetry(30L);
    }

    @Test
    void retryMissingFailedPushLogUsesNotFoundError() {
        when(pushLogMapper.resetFailedForRetry(30L)).thenReturn(0);
        assertThrows(AlarmNotFoundException.class, () -> alarmService.retryPush(30L));
    }

    @Test
    void createPreservesMillisecondsAndUsesBoundedUniqueNumber() {
        LocalDateTime alarmTime = LocalDateTime.of(2026, 8, 5, 14, 30, 0, 456_000_000);
        when(alarmRecordMapper.insert(any(AlarmRecord.class))).thenAnswer(invocation -> {
            AlarmRecord record = invocation.getArgument(0);
            record.setId(100L);
            return 1;
        });
        AlarmCreateDTO dto = new AlarmCreateDTO();
        dto.setProjectId(1L);
        dto.setSourceType(2);
        dto.setAlarmLevel(1);
        dto.setAlarmTypeCode("PM25_EXCEED");
        dto.setAlarmContent("PM2.5超标");
        dto.setAlarmTime(alarmTime);

        assertEquals(100L, alarmService.create(dto));

        ArgumentCaptor<AlarmRecord> captor = ArgumentCaptor.forClass(AlarmRecord.class);
        verify(alarmRecordMapper).insert(captor.capture());
        assertEquals(alarmTime, captor.getValue().getAlarmTime());
        assertTrue(captor.getValue().getAlarmNo().length() <= 32);
    }

    @Test
    void rejectsInvalidTrendRange() {
        assertThrows(AlarmException.class, () -> alarmService.trend(1L,
                java.time.LocalDate.of(2026, 8, 2), java.time.LocalDate.of(2026, 8, 1),
                "DAY", null, null));
    }

    @Test
    void dailyStatisticsIsReadOnly() {
        LocalDate date = LocalDate.of(2026, 8, 5);
        when(dailyStatMapper.selectByProjectAndDate(1L, date)).thenReturn(List.of());

        alarmService.dailyStatistics(1L, date);

        verify(dailyStatMapper, never()).deleteByProjectAndDate(any(), any());
        verify(dailyStatMapper, never()).insertAggregate(any(), any(), any());
    }

    @Test
    void missingRecoveryUsesNotFoundError() {
        AlarmListVO detail = new AlarmListVO();
        detail.setId(10L);
        detail.setProjectId(1L);
        detail.setAlarmTime(LocalDateTime.of(2026, 8, 5, 14, 30));
        when(alarmRecordMapper.selectDetail(10L)).thenReturn(detail);
        when(recoveryMapper.selectHistory(10L)).thenReturn(null);

        assertThrows(AlarmNotFoundException.class, () -> alarmService.recovery(10L));
    }

    @Test
    void xlsxKeepsCommaInsideSingleCell() throws IOException {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 31, 23, 59, 59);
        AlarmListVO row = new AlarmListVO();
        row.setAlarmNo("AL001");
        row.setAlarmContent("现场核查,已完成");
        when(alarmRecordMapper.selectForExport(1L, start, end, 2, 1, 0, 5L)).thenReturn(List.of(row));

        byte[] workbook = alarmService.export(1L, start, end, "XLSX", 2, 1, 0, 5L);
        String sheetXml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    sheetXml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        assertTrue(sheetXml != null && sheetXml.contains("现场核查,已完成"));
    }
    private static AlarmRecord alarmRecord(Long id) {
        AlarmRecord record = new AlarmRecord();
        record.setId(id);
        record.setProjectId(1L);
        record.setAlarmTime(LocalDateTime.of(2026, 8, 5, 14, 30));
        record.setStatus(0);
        record.setIsDeleted(0);
        return record;
    }

    private static AlarmDisposalDTO disposalRequest(int conclusion) {
        AlarmDisposalDTO dto = new AlarmDisposalDTO();
        dto.setAlarmId(10L);
        dto.setDisposalContent("核查并完成整改");
        dto.setDisposalConclusion(conclusion);
        return dto;
    }
}