package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartbuildings.common.SprayConstants;
import com.qst.smartbuildings.domain.SprayOperationLog;
import com.qst.smartbuildings.service.impl.SprayServiceImpl;
import com.qst.smartbuildings.domain.SprayScheduleTask;
import com.qst.smartbuildings.mapper.SprayOperationLogMapper;
import com.qst.smartbuildings.mapper.SprayScheduleTaskMapper;
import com.qst.smartbuildings.util.SprayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 喷淋定时任务调度器
 * <p>每分钟轮询所有启用中的定时任务，按周期与当天时间窗自动记录 schedule 启停日志，
 * 喷淋运行状态由最新操作日志动态推算（与手动/自动联动共用同一套状态模型）。</p>
 */
@Component
public class SprayScheduler {

    private static final Logger log = LoggerFactory.getLogger(SprayScheduler.class);

    private final SprayScheduleTaskMapper scheduleMapper;
    private final SprayOperationLogMapper opLogMapper;
    private final SprayServiceImpl sprayService;

    public SprayScheduler(SprayScheduleTaskMapper scheduleMapper, SprayOperationLogMapper opLogMapper,
                           SprayServiceImpl sprayService) {
        this.scheduleMapper = scheduleMapper;
        this.opLogMapper = opLogMapper;
        this.sprayService = sprayService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void tick() {
        List<SprayScheduleTask> tasks = scheduleMapper.selectList(
                new LambdaQueryWrapper<SprayScheduleTask>().eq(SprayScheduleTask::getStatus, SprayConstants.STATUS_ENABLED));
        if (tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SprayScheduleTask task : tasks) {
            try {
                process(task, now);
            } catch (Exception e) {
                log.error("喷淋定时任务处理失败 taskId={}", task.getId(), e);
            }
        }
    }

    private void process(SprayScheduleTask task, LocalDateTime now) {
        if (task.getStartTime() == null || task.getDurationMinutes() == null || task.getDurationMinutes() <= 0) {
            return;
        }
        LocalDate today = now.toLocalDate();
        if (!matchesToday(task, today)) {
            return;
        }
        LocalDateTime windowStart = today.atTime(task.getStartTime());
        LocalDateTime windowEnd = windowStart.plusMinutes(task.getDurationMinutes());
        if (!now.isBefore(windowStart) && now.isBefore(windowEnd)) {
            // 窗口内：确保已写入当天启动日志
            if (countWindowLog(task.getLocationId(), SprayConstants.TRIGGER_SCHEDULE, SprayConstants.OP_START, windowStart, windowEnd) == 0) {
                insertLog(task.getLocationId(), SprayConstants.TRIGGER_SCHEDULE, SprayConstants.OP_START, "定时任务启动：" + task.getTaskName());
            }
        } else if (now.isAfter(windowEnd)) {
            // 窗口已过：确保已写入当天停止日志。
            // 注意：停止日志的写入时刻(now)在 windowEnd 之后，若仍用 between(windowStart, windowEnd) 去重，
            // 已写入的停止日志其 createTime 落在窗口外，会导致每分钟都判定为「未记录」而重复插入。
            // 改用 createTime >= windowStart 去重，保证每个窗口只记一次停止。
            long stopCount = opLogMapper.selectCount(new LambdaQueryWrapper<SprayOperationLog>()
                    .eq(SprayOperationLog::getLocationId, task.getLocationId())
                    .eq(SprayOperationLog::getTriggerMode, SprayConstants.TRIGGER_SCHEDULE)
                    .eq(SprayOperationLog::getOperationType, SprayConstants.OP_STOP)
                    .ge(SprayOperationLog::getCreateTime, windowStart));
            if (stopCount == 0) {
                insertLog(task.getLocationId(), SprayConstants.TRIGGER_SCHEDULE, SprayConstants.OP_STOP, "定时任务结束：" + task.getTaskName());
            }
        }
    }

    private boolean matchesToday(SprayScheduleTask task, LocalDate today) {
        String cycle = task.getCycleType();
        String cfg = task.getCycleConfig();
        if (SprayConstants.CYCLE_DAILY.equals(cycle)) {
            return true;
        }
        if (SprayConstants.CYCLE_WEEKLY.equals(cycle)) {
            Set<String> days = SprayUtil.parseWeekdays(cfg);
            return days.isEmpty() || days.contains(SprayUtil.weekdayShort(today.getDayOfWeek()));
        }
        if (SprayConstants.CYCLE_MONTHLY.equals(cycle)) {
            Set<Integer> days = SprayUtil.parseMonthDays(cfg);
            return days.isEmpty() || days.contains(today.getDayOfMonth());
        }
        return false;
    }

    private long countWindowLog(Long locationId, String triggerMode, String opType,
                                LocalDateTime start, LocalDateTime end) {
        return opLogMapper.selectCount(new LambdaQueryWrapper<SprayOperationLog>()
                .eq(SprayOperationLog::getLocationId, locationId)
                .eq(SprayOperationLog::getTriggerMode, triggerMode)
                .eq(SprayOperationLog::getOperationType, opType)
                .between(SprayOperationLog::getCreateTime, start, end));
    }

    private void insertLog(Long locationId, String triggerMode, String opType, String reason) {
        SprayOperationLog log = new SprayOperationLog();
        log.setLocationId(locationId);
        log.setSensorId(null);
        log.setTriggerMode(triggerMode);
        log.setOperationType(opType);
        log.setReason(reason);
        log.setOperatorId(SprayConstants.AUTO_OPERATOR_ID);
        log.setOperatorName(SprayConstants.AUTO_OPERATOR);
        log.setCreateTime(LocalDateTime.now());
        opLogMapper.insert(log);
        // 启动/停止属于真实喷淋动作，调用统一设备控制方法（当前为逻辑调用占位，终端输出开启/关闭）
        if (SprayConstants.OP_START.equals(opType) || SprayConstants.OP_STOP.equals(opType)) {
            sprayService.controlDevice(locationId, opType, reason);
        }
    }
}
