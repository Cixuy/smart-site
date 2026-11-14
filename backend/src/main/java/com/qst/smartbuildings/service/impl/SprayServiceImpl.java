package com.qst.smartbuildings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qst.smartbuildings.common.AuthPrincipal;
import com.qst.smartbuildings.common.UserContext;
import com.qst.smartbuildings.common.SprayConstants;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.domain.SprayOperationLog;
import com.qst.smartbuildings.domain.SprayScheduleTask;
import com.qst.smartbuildings.dto.SprayConflictCheckRequest;
import com.qst.smartbuildings.dto.SprayOperateRequest;
import com.qst.smartbuildings.dto.SprayOperationLogPageRequest;
import com.qst.smartbuildings.dto.SpraySchedulePageRequest;
import com.qst.smartbuildings.dto.SprayScheduleRequest;
import com.qst.smartbuildings.dto.SprayStatisticsRequest;
import com.qst.smartbuildings.dto.SprayTriggerRequest;
import com.qst.smartbuildings.dto.UserVO;
import com.qst.smartbuildings.entity.BPage;
import com.qst.smartbuildings.exception.SprayException;
import com.qst.smartbuildings.mapper.AssetDeviceLocationMapper;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.mapper.SprayOperationLogMapper;
import com.qst.smartbuildings.mapper.SprayScheduleTaskMapper;
import com.qst.smartbuildings.service.SprayService;
import com.qst.smartbuildings.util.SprayUtil;
import com.qst.smartbuildings.vo.SprayActiveTaskVO;
import com.qst.smartbuildings.vo.SprayOperateResultVO;
import com.qst.smartbuildings.vo.SprayOperationLogVO;
import com.qst.smartbuildings.vo.SprayScheduleVO;
import com.qst.smartbuildings.vo.SprayStatisticsDetailVO;
import com.qst.smartbuildings.vo.SprayStatisticsVO;
import com.qst.smartbuildings.vo.SprayStatusVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 喷淋降尘业务实现
 */
@Service
public class SprayServiceImpl implements SprayService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(SprayServiceImpl.class);
    /** 设备指令专用 logger（固定名 "DeviceComm"，配合 logback 单独落文件 + IDEA 过滤） */
    private static final Logger deviceLog = LoggerFactory.getLogger("DeviceComm");

    private final SprayOperationLogMapper opLogMapper;
    private final SprayScheduleTaskMapper scheduleMapper;
    private final AssetDeviceLocationMapper locationMapper;
    private final AssetDeviceSensorMapper sensorMapper;
    // 认证主体由 AuthInterceptor 在请求入口解析并写入 UserContext；本类只读取，不再持有认证字段。

    public SprayServiceImpl(SprayOperationLogMapper opLogMapper,
                            SprayScheduleTaskMapper scheduleMapper,
                            AssetDeviceLocationMapper locationMapper,
                            AssetDeviceSensorMapper sensorMapper) {
        this.opLogMapper = opLogMapper;
        this.scheduleMapper = scheduleMapper;
        this.locationMapper = locationMapper;
        this.sensorMapper = sensorMapper;
    }

    // ======================== 权限 ========================

    private UserVO currentUser() {
        AuthPrincipal p = UserContext.get();
        if (p == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(p.getUserId());
        vo.setUsername(p.getUsername());
        vo.setRealName(p.getRealName());
        return vo;
    }

    private void assertSafetyAdmin() {
        // 权限逻辑已临时注释（开发/联调期免鉴权）：拦截器在 auth.enabled=false 时
        // 已注入超管主体；如需恢复权限校验，取消以下注释即可。
//        AuthPrincipal p = UserContext.get();
//        if (p == null || p.getUserId() == null) {
//            throw new SprayException(SystemCodeEnum.SYSTEM_NO_AUTH, "请先登录");
//        }
//        boolean ok = p.getRoles().stream().anyMatch(this::isAdminOrSafetyRole);
//        if (!ok) {
//            throw new SprayException(SystemCodeEnum.SYSTEM_NO_AUTH, "仅安全管理员可操作");
//        }
    }

    private boolean isAdminOrSafetyRole(String role) {
        if (role == null) {
            return false;
        }
        String r = role.toUpperCase();
        return r.contains("ADMIN") || r.contains("SAFETY")
                || r.contains("安全管理员") || r.contains("管理员");
    }

    private String operatorName(UserVO u) {
        if (u == null) {
            return SprayConstants.AUTO_OPERATOR;
        }
        if (StringUtils.hasText(u.getRealName())) {
            return u.getRealName();
        }
        return StringUtils.hasText(u.getUsername()) ? u.getUsername() : SprayConstants.AUTO_OPERATOR;
    }

    // ======================== 工具 ========================

    private String locationName(Long locationId) {
        if (locationId == null) {
            return null;
        }
        AssetDeviceLocation loc = locationMapper.selectById(locationId);
        return loc == null ? null : loc.getLocationName();
    }

    private void checkLocationExists(Long locationId) {
        AssetDeviceLocation loc = locationMapper.selectById(locationId);
        if (loc == null) {
            throw new SprayException(SystemCodeEnum.NOT_FOUND, "位置不存在");
        }
    }

    private SprayStatusVO deriveStatus(Long locationId) {
        SprayOperationLog latest = opLogMapper.selectLatestByLocation(locationId);
        SprayStatusVO vo = new SprayStatusVO();
        vo.setLocationId(locationId);
        vo.setLocationName(locationName(locationId));
        if (latest == null) {
            vo.setIsRunning(false);
            return vo;
        }
        boolean running = SprayConstants.OP_START.equals(latest.getOperationType());
        vo.setIsRunning(running);
        if (running) {
            vo.setLastStartTime(fmt(latest.getCreateTime()));
            vo.setTriggerMode(latest.getTriggerMode());
        }
        return vo;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DT_FMT);
    }

    private static String fmt(LocalTime t) {
        return t == null ? null : t.format(TIME_FMT);
    }

    private SprayOperateResultVO toOperateResult(SprayOperationLog log, String status) {
        SprayOperateResultVO vo = new SprayOperateResultVO();
        vo.setOperationId(log.getId());
        vo.setLocationId(log.getLocationId());
        vo.setLocationName(locationName(log.getLocationId()));
        vo.setTriggerMode(log.getTriggerMode());
        vo.setStatus(status);
        return vo;
    }

    // ======================== 设备指令 / 建档日志 ========================

    /**
     * 启停物理喷淋设备（逻辑调用占位）。
     * <p>当前真实的「物理设备通信模块」尚未实现，这里仅在终端输出逻辑上的开启/关闭；
     * 后续该模块落地后，把下方打印替换为对其接口的调用即可。</p>
     */
    public void controlDevice(Long locationId, String action, String reason) {
        deviceLog.info(">>> [设备指令] device=SPRAY-LOC-{} action={} reason={} (物理设备通信模块未实现，逻辑调用)",
                locationId, action, reason);
    }

    private SprayScheduleVO toScheduleVO(SprayScheduleTask t) {
        SprayScheduleVO vo = new SprayScheduleVO();
        vo.setId(t.getId());
        vo.setTaskName(t.getTaskName());
        vo.setLocationId(t.getLocationId());
        vo.setLocationName(locationName(t.getLocationId()));
        vo.setStartTime(fmt(t.getStartTime()));
        vo.setDurationMinutes(t.getDurationMinutes());
        vo.setCycleType(t.getCycleType());
        vo.setCycleConfig(t.getCycleConfig());
        vo.setStatus(t.getStatus());
        vo.setRemark(t.getRemark());
        vo.setCreateTime(fmt(t.getCreateTime()));
        vo.setUpdateTime(fmt(t.getUpdateTime()));
        return vo;
    }

    // ======================== 手动启停 ========================

    @Override
    public SprayOperateResultVO start(SprayOperateRequest req) {
        assertSafetyAdmin();
        Long loc = req.getLocationId();
        if (loc == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少位置ID");
        }
        checkLocationExists(loc);
        if (Boolean.TRUE.equals(deriveStatus(loc).getIsRunning())) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "该区域喷淋正在运行，无需重复启动");
        }
        UserVO u = currentUser();
        SprayOperationLog log = new SprayOperationLog();
        log.setLocationId(loc);
        log.setSensorId(null);
        log.setTriggerMode(SprayConstants.TRIGGER_MANUAL);
        log.setOperationType(SprayConstants.OP_START);
        log.setReason(req.getReason());
        log.setOperatorId(u == null ? null : u.getId());
        log.setOperatorName(operatorName(u));
        log.setCreateTime(LocalDateTime.now());
        opLogMapper.insert(log);
        controlDevice(loc, SprayConstants.OP_START, "手动启动喷淋");
        return toOperateResult(log, "running");
    }

    @Override
    public SprayOperateResultVO stop(SprayOperateRequest req) {
        assertSafetyAdmin();
        Long loc = req.getLocationId();
        if (loc == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少位置ID");
        }
        checkLocationExists(loc);
        if (!Boolean.TRUE.equals(deriveStatus(loc).getIsRunning())) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "喷淋已处于停止状态");
        }
        UserVO u = currentUser();
        SprayOperationLog log = new SprayOperationLog();
        log.setLocationId(loc);
        log.setSensorId(null);
        log.setTriggerMode(SprayConstants.TRIGGER_MANUAL);
        log.setOperationType(SprayConstants.OP_STOP);
        log.setReason(req.getReason());
        log.setOperatorId(u == null ? null : u.getId());
        log.setOperatorName(operatorName(u));
        log.setCreateTime(LocalDateTime.now());
        opLogMapper.insert(log);
        controlDevice(loc, SprayConstants.OP_STOP, "手动停止喷淋");
        return toOperateResult(log, "stopped");
    }

    @Override
    public SprayStatusVO status(Long locationId) {
        if (locationId == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少位置ID");
        }
        return deriveStatus(locationId);
    }

    @Override
    public List<SprayStatusVO> statusBatch(Long parentLocationId) {
        List<AssetDeviceLocation> all = locationMapper.selectList(
                new LambdaQueryWrapper<AssetDeviceLocation>()
                        .eq(AssetDeviceLocation::getStatus, 1)
                        .eq(AssetDeviceLocation::getIsDeleted, 0));
        Set<Long> ids = new HashSet<>();
        if (parentLocationId == null) {
            all.forEach(l -> ids.add(l.getId()));
        } else {
            Set<Long> validIds = all.stream()
                    .map(AssetDeviceLocation::getId)
                    .collect(Collectors.toSet());
            if (!validIds.contains(parentLocationId)) {
                return new ArrayList<>();
            }
            Map<Long, List<Long>> childrenMap = all.stream()
                    .filter(l -> l.getParentId() != null)
                    .collect(Collectors.groupingBy(AssetDeviceLocation::getParentId,
                            Collectors.mapping(AssetDeviceLocation::getId, Collectors.toList())));
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(parentLocationId);
            while (!stack.isEmpty()) {
                Long cur = stack.pop();
                ids.add(cur);
                List<Long> children = childrenMap.get(cur);
                if (children != null) {
                    children.forEach(stack::push);
                }
            }
        }
        List<SprayStatusVO> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(deriveStatus(id));
        }
        return result;
    }

    // ======================== 自动联动 ========================

    @Override
    public SprayOperateResultVO trigger(SprayTriggerRequest req) {
        Long loc = req.getLocationId();
        Long sensorId = req.getSensorId();
        if (loc == null || sensorId == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少位置ID或监测点ID");
        }
        checkLocationExists(loc);
        AssetDeviceSensor sensor = sensorMapper.selectById(sensorId);
        if (sensor == null) {
            throw new SprayException(SystemCodeEnum.NOT_FOUND, "监测点不存在");
        }
        BigDecimal threshold = sensor.getSprayThreshold();
        if (threshold == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "监测点未配置喷淋联动阈值");
        }
        BigDecimal metric = req.getMetricValue();
        if (metric == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少触发数值");
        }
        SprayOperationLog latest = opLogMapper.selectLatestByLocation(loc);
        LocalDateTime now = LocalDateTime.now();
        // 手动操作后 1 小时内自动联动暂停
        if (latest != null && SprayConstants.TRIGGER_MANUAL.equals(latest.getTriggerMode())
                && latest.getCreateTime().isAfter(now.minusHours(1))) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "手动操作后1小时内自动联动已暂停");
        }
        // 5 分钟防抖
        if (latest != null && SprayConstants.TRIGGER_AUTO.equals(latest.getTriggerMode())
                && latest.getCreateTime().isAfter(now.minusMinutes(5))) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "5分钟内已触发自动联动，防抖中");
        }
        boolean running = Boolean.TRUE.equals(deriveStatus(loc).getIsRunning());
        SprayOperationLog log = new SprayOperationLog();
        log.setLocationId(loc);
        log.setSensorId(sensorId);
        log.setTriggerMode(SprayConstants.TRIGGER_AUTO);
        log.setOperatorId(SprayConstants.AUTO_OPERATOR_ID);
        log.setOperatorName(SprayConstants.AUTO_OPERATOR);
        log.setCreateTime(now);
        if (!running && metric.compareTo(threshold) >= 0) {
            log.setOperationType(SprayConstants.OP_START);
            log.setReason("PM超标自动启动，监测值=" + metric + "，阈值=" + threshold);
            opLogMapper.insert(log);
            controlDevice(loc, SprayConstants.OP_START, "PM超标自动启动");
            return toOperateResult(log, "running");
        } else if (running && metric.compareTo(threshold.multiply(SprayConstants.THRESHOLD_HYSTERESIS)) <= 0) {
            log.setOperationType(SprayConstants.OP_STOP);
            log.setReason("PM回落自动关闭，监测值=" + metric + "，阈值×0.9=" + threshold.multiply(SprayConstants.THRESHOLD_HYSTERESIS));
            opLogMapper.insert(log);
            controlDevice(loc, SprayConstants.OP_STOP, "PM回落自动关闭");
            return toOperateResult(log, "stopped");
        } else {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST,
                    running ? "监测值未低于阈值×90%，无需关闭" : "监测值未达联动阈值，无需启动");
        }
    }

    // ======================== 定时任务 ========================

    @Override
    public SprayScheduleVO createSchedule(SprayScheduleRequest req) {
        assertSafetyAdmin();
        validateSchedule(req);
        checkLocationExists(req.getLocationId());
        if (conflictExists(null, req.getLocationId(), req.getStartTime(),
                req.getDurationMinutes(), req.getCycleType(), req.getCycleConfig())) {
            throw new SprayException(SystemCodeEnum.CONFLICT, "同一位置存在时间冲突的启用任务");
        }
        SprayScheduleTask task = new SprayScheduleTask();
        fillTask(task, req);
        task.setStatus(SprayConstants.STATUS_ENABLED);
        task.setIsDeleted(SprayConstants.NOT_DELETED);
        task.setCreateTime(LocalDateTime.now());
        scheduleMapper.insert(task);
        return toScheduleVO(task);
    }

    @Override
    public SprayScheduleVO updateSchedule(SprayScheduleRequest req) {
        assertSafetyAdmin();
        if (req.getId() == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少任务ID");
        }
        SprayScheduleTask existing = scheduleMapper.selectById(req.getId());
        if (existing == null || (existing.getIsDeleted() != null && existing.getIsDeleted() == 1)) {
            throw new SprayException(SystemCodeEnum.NOT_FOUND, "定时任务不存在");
        }
        validateSchedule(req);
        checkLocationExists(req.getLocationId());
        if (conflictExists(req.getId(), req.getLocationId(), req.getStartTime(),
                req.getDurationMinutes(), req.getCycleType(), req.getCycleConfig())) {
            throw new SprayException(SystemCodeEnum.CONFLICT, "同一位置存在时间冲突的启用任务");
        }
        fillTask(existing, req);
        existing.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(existing);
        return toScheduleVO(existing);
    }

    @Override
    public SprayScheduleVO scheduleDetail(Long id) {
        SprayScheduleTask t = scheduleMapper.selectById(id);
        if (t == null || (t.getIsDeleted() != null && t.getIsDeleted() == 1)) {
            throw new SprayException(SystemCodeEnum.NOT_FOUND, "定时任务不存在");
        }
        return toScheduleVO(t);
    }

    @Override
    public IPage<SprayScheduleVO> schedulePage(SpraySchedulePageRequest req) {
        Integer pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        Integer pageSize = req.getPageSize() == null ? 20 : req.getPageSize();
        BPage<SprayScheduleTask> page = new BPage<>(pageNum, pageSize);
        LambdaQueryWrapper<SprayScheduleTask> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getTaskName())) {
            w.like(SprayScheduleTask::getTaskName, req.getTaskName());
        }
        if (req.getLocationId() != null) {
            w.eq(SprayScheduleTask::getLocationId, req.getLocationId());
        }
        if (StringUtils.hasText(req.getCycleType())) {
            w.eq(SprayScheduleTask::getCycleType, req.getCycleType());
        }
        if (req.getStatus() != null) {
            w.eq(SprayScheduleTask::getStatus, req.getStatus());
        }
        w.orderByDesc(SprayScheduleTask::getId);
        IPage<SprayScheduleTask> res = scheduleMapper.selectPage(page, w);
        BPage<SprayScheduleVO> result = new BPage<>(res.getCurrent(), res.getSize(), res.getTotal());
        result.setRecords(res.getRecords().stream().map(this::toScheduleVO).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void deleteSchedule(List<Long> ids) {
        assertSafetyAdmin();
        if (ids == null || ids.isEmpty()) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "缺少任务ID");
        }
        for (Long id : ids) {
            scheduleMapper.deleteById(id); // @TableLogic -> 逻辑删除
        }
    }

    @Override
    public void scheduleStatus(Long id, Integer status) {
        assertSafetyAdmin();
        SprayScheduleTask t = scheduleMapper.selectById(id);
        if (t == null || (t.getIsDeleted() != null && t.getIsDeleted() == 1)) {
            throw new SprayException(SystemCodeEnum.NOT_FOUND, "定时任务不存在");
        }
        if (status == null || (status != SprayConstants.STATUS_DISABLED && status != SprayConstants.STATUS_ENABLED)) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "状态仅支持 0 或 1");
        }
        t.setStatus(status);
        t.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(t);
    }

    @Override
    public boolean conflictCheck(SprayConflictCheckRequest req) {
        return conflictExists(req.getExcludeId(), req.getLocationId(), req.getStartTime(),
                req.getDurationMinutes(), req.getCycleType(), req.getCycleConfig());
    }

    @Override
    public List<SprayActiveTaskVO> activeTasks() {
        List<SprayScheduleTask> tasks = scheduleMapper.selectList(
                new LambdaQueryWrapper<SprayScheduleTask>().eq(SprayScheduleTask::getStatus, SprayConstants.STATUS_ENABLED));
        List<SprayActiveTaskVO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SprayScheduleTask t : tasks) {
            SprayActiveTaskVO vo = new SprayActiveTaskVO();
            vo.setId(t.getId());
            vo.setTaskName(t.getTaskName());
            vo.setLocationId(t.getLocationId());
            vo.setLocationName(locationName(t.getLocationId()));
            vo.setStartTime(fmt(t.getStartTime()));
            vo.setDurationMinutes(t.getDurationMinutes());
            vo.setCycleType(t.getCycleType());
            vo.setCycleConfig(t.getCycleConfig());
            vo.setNextExecuteTime(fmt(nextRunTime(t, now)));
            result.add(vo);
        }
        return result;
    }

    // ======================== 操作日志 ========================

    @Override
    public IPage<SprayOperationLogVO> logPage(SprayOperationLogPageRequest req) {
        Integer pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        Integer pageSize = req.getPageSize() == null ? 20 : req.getPageSize();
        BPage<SprayOperationLogVO> page = new BPage<>(pageNum, pageSize);
        return opLogMapper.selectPageVo(page, req.getLocationId(), req.getTriggerMode(),
                req.getOperationType(), req.getOperatorName(), req.getBeginTime(), req.getEndTime());
    }

    @Override
    public SprayOperationLogVO logDetail(Long id) {
        SprayOperationLog log = opLogMapper.selectById(id);
        if (log == null) {
            throw new SprayException(SystemCodeEnum.NOT_FOUND, "操作记录不存在");
        }
        SprayOperationLogVO vo = new SprayOperationLogVO();
        vo.setId(log.getId());
        vo.setLocationId(log.getLocationId());
        vo.setLocationName(locationName(log.getLocationId()));
        vo.setSensorId(log.getSensorId());
        vo.setTriggerMode(log.getTriggerMode());
        vo.setOperationType(log.getOperationType());
        vo.setReason(log.getReason());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setCreateTime(fmt(log.getCreateTime()));
        return vo;
    }

    @Override
    public void exportLog(SprayOperationLogPageRequest req, HttpServletResponse response) {
        BPage<SprayOperationLogVO> page = new BPage<>(1, 100000);
        IPage<SprayOperationLogVO> res = opLogMapper.selectPageVo(page, req.getLocationId(), req.getTriggerMode(),
                req.getOperationType(), req.getOperatorName(), req.getBeginTime(), req.getEndTime());
        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=spray_operation_log.csv");
        try (OutputStream os = response.getOutputStream()) {
            os.write(0xEF); os.write(0xBB); os.write(0xBF); // UTF-8 BOM
            StringBuilder sb = new StringBuilder();
            sb.append("操作ID,位置ID,位置名称,监测点ID,触发方式,操作类型,原因,操作人ID,操作人,操作时间\n");
            for (SprayOperationLogVO v : res.getRecords()) {
                sb.append(csvCell(v.getId()))
                        .append(',').append(csvCell(v.getLocationId()))
                        .append(',').append(csvCell(v.getLocationName()))
                        .append(',').append(csvCell(v.getSensorId()))
                        .append(',').append(csvCell(v.getTriggerMode()))
                        .append(',').append(csvCell(v.getOperationType()))
                        .append(',').append(csvCell(v.getReason()))
                        .append(',').append(csvCell(v.getOperatorId()))
                        .append(',').append(csvCell(v.getOperatorName()))
                        .append(',').append(csvCell(v.getCreateTime()))
                        .append('\n');
            }
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            throw new SprayException(SystemCodeEnum.SYSTEM_ERROR, "导出失败：" + e.getMessage());
        }
    }

    // ======================== 统计 ========================

    @Override
    public SprayStatisticsVO statistics(SprayStatisticsRequest req) {
        if (!StringUtils.hasText(req.getBeginTime()) || !StringUtils.hasText(req.getEndTime())) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "开始/结束时间为必填");
        }
        LocalDateTime begin;
        LocalDateTime end;
        try {
            begin = LocalDateTime.parse(req.getBeginTime(), DT_FMT);
            end = LocalDateTime.parse(req.getEndTime(), DT_FMT);
        } catch (Exception e) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        if (begin.isAfter(end)) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "开始时间不能晚于结束时间");
        }
        List<SprayOperationLog> logs = opLogMapper.selectInRange(req.getLocationId(),
                req.getBeginTime(), req.getEndTime());

        long totalStart = 0;
        long totalDuration = 0;
        Map<String, Long> startByMode = new HashMap<>();
        Map<String, SprayStatisticsDetailVO> detailMap = new HashMap<>();

        Map<Long, SprayOperationLog> openStart = new HashMap<>();
        for (SprayOperationLog log : logs) {
            if (SprayConstants.OP_START.equals(log.getOperationType())) {
                openStart.put(log.getLocationId(), log);
            } else if (SprayConstants.OP_STOP.equals(log.getOperationType())) {
                SprayOperationLog s = openStart.remove(log.getLocationId());
                if (s != null) {
                    accumulate(s, log.getCreateTime(), begin, end, req.getGroupBy(),
                            detailMap, startByMode);
                    totalStart++;
                    long minutes = Math.max(0, ChronoUnit.MINUTES.between(s.getCreateTime(), log.getCreateTime()));
                    totalDuration += minutes;
                }
            }
        }
        // 窗口结束仍未停止的，按结束时间收尾
        for (SprayOperationLog s : openStart.values()) {
            accumulate(s, end, begin, end, req.getGroupBy(), detailMap, startByMode);
            totalStart++;
            long minutes = Math.max(0, ChronoUnit.MINUTES.between(s.getCreateTime(), end));
            totalDuration += minutes;
        }

        SprayStatisticsVO vo = new SprayStatisticsVO();
        vo.setTotalStartCount(totalStart);
        vo.setTotalDurationMinutes(totalDuration);
        vo.setAvgDurationMinutes(totalStart > 0 ? totalDuration / totalStart : 0);
        if (totalStart > 0) {
            vo.setAutoTriggerRate(rate(startByMode.getOrDefault(SprayConstants.TRIGGER_AUTO, 0L), totalStart));
            vo.setManualTriggerRate(rate(startByMode.getOrDefault(SprayConstants.TRIGGER_MANUAL, 0L), totalStart));
            vo.setScheduleTriggerRate(rate(startByMode.getOrDefault(SprayConstants.TRIGGER_SCHEDULE, 0L), totalStart));
        } else {
            vo.setAutoTriggerRate(BigDecimal.ZERO);
            vo.setManualTriggerRate(BigDecimal.ZERO);
            vo.setScheduleTriggerRate(BigDecimal.ZERO);
        }
        vo.setDetails(new ArrayList<>(detailMap.values()));
        return vo;
    }

    private void accumulate(SprayOperationLog startLog, LocalDateTime stopTime,
                            LocalDateTime begin, LocalDateTime end, String groupBy,
                            Map<String, SprayStatisticsDetailVO> detailMap,
                            Map<String, Long> startByMode) {
        startByMode.merge(startLog.getTriggerMode(), 1L, Long::sum);
        String key = groupKey(startLog, groupBy);
        SprayStatisticsDetailVO d = detailMap.computeIfAbsent(key,
                k -> {
                    SprayStatisticsDetailVO v = new SprayStatisticsDetailVO();
                    v.setGroupKey(k);
                    v.setStartCount(0L);
                    v.setDurationMinutes(0L);
                    return v;
                });
        d.setStartCount(d.getStartCount() + 1);
        long minutes = Math.max(0, ChronoUnit.MINUTES.between(startLog.getCreateTime(), stopTime));
        d.setDurationMinutes(d.getDurationMinutes() + minutes);
    }

    private String groupKey(SprayOperationLog log, String groupBy) {
        if ("day".equalsIgnoreCase(groupBy)) {
            return log.getCreateTime().toLocalDate().toString();
        } else if ("location".equalsIgnoreCase(groupBy)) {
            return locationName(log.getLocationId());
        } else if ("triggerMode".equalsIgnoreCase(groupBy)) {
            return log.getTriggerMode();
        }
        return "全部";
    }

    private BigDecimal rate(long count, long total) {
        return BigDecimal.valueOf(count * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    // ======================== 定时任务辅助 ========================

    private void validateSchedule(SprayScheduleRequest req) {
        if (!StringUtils.hasText(req.getTaskName())) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "任务名称不能为空");
        }
        if (req.getLocationId() == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "执行位置不能为空");
        }
        if (req.getStartTime() == null) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "开始时间不能为空");
        }
        if (req.getDurationMinutes() == null || req.getDurationMinutes() <= 0) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "持续时长必须为正整数");
        }
        if (!List.of(SprayConstants.CYCLE_DAILY, SprayConstants.CYCLE_WEEKLY, SprayConstants.CYCLE_MONTHLY).contains(req.getCycleType())) {
            throw new SprayException(SystemCodeEnum.BAD_REQUEST, "执行周期仅支持 daily/weekly/monthly");
        }
    }

    private void fillTask(SprayScheduleTask task, SprayScheduleRequest req) {
        task.setTaskName(req.getTaskName());
        task.setLocationId(req.getLocationId());
        task.setStartTime(req.getStartTime());
        task.setDurationMinutes(req.getDurationMinutes());
        task.setCycleType(req.getCycleType());
        task.setCycleConfig(req.getCycleConfig());
        task.setRemark(req.getRemark());
    }

    private boolean conflictExists(Long excludeId, Long locationId, LocalTime start, int duration,
                                   String cycleType, String cycleConfig) {
        if (locationId == null || start == null || duration <= 0) {
            return false;
        }
        List<SprayScheduleTask> tasks = scheduleMapper.selectList(
                new LambdaQueryWrapper<SprayScheduleTask>()
                        .eq(SprayScheduleTask::getLocationId, locationId)
                        .eq(SprayScheduleTask::getStatus, SprayConstants.STATUS_ENABLED));
        LocalTime candEnd = start.plusMinutes(duration);
        for (SprayScheduleTask t : tasks) {
            if (excludeId != null && excludeId.equals(t.getId())) {
                continue;
            }
            if (t.getStartTime() == null || t.getDurationMinutes() == null) {
                continue;
            }
            if (!timeOverlap(start, candEnd, t.getStartTime(), t.getStartTime().plusMinutes(t.getDurationMinutes()))) {
                continue;
            }
            if (cycleOverlap(cycleType, cycleConfig, t.getCycleType(), t.getCycleConfig())) {
                return true;
            }
        }
        return false;
    }

    private boolean timeOverlap(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private boolean cycleOverlap(String a, String b, String cfgA, String cfgB) {
        if (SprayConstants.CYCLE_DAILY.equals(a) || SprayConstants.CYCLE_DAILY.equals(b)) {
            return true; // daily 覆盖每一天，与任何周期日期均有交集
        }
        if (SprayConstants.CYCLE_WEEKLY.equals(a) && SprayConstants.CYCLE_WEEKLY.equals(b)) {
            return weekdaysIntersect(cfgA, cfgB);
        }
        if (SprayConstants.CYCLE_MONTHLY.equals(a) && SprayConstants.CYCLE_MONTHLY.equals(b)) {
            return monthDaysIntersect(cfgA, cfgB);
        }
        // weekly vs monthly：保守判定为冲突（日期不可直接比较）
        return true;
    }

    private boolean weekdaysIntersect(String cfgA, String cfgB) {
        Set<String> setA = SprayUtil.parseWeekdays(cfgA);
        Set<String> setB = SprayUtil.parseWeekdays(cfgB);
        if (setA.isEmpty() || setB.isEmpty()) {
            return true; // 未配置则视为每天都命中
        }
        return setA.stream().anyMatch(setB::contains);
    }

    private boolean monthDaysIntersect(String cfgA, String cfgB) {
        Set<Integer> setA = SprayUtil.parseMonthDays(cfgA);
        Set<Integer> setB = SprayUtil.parseMonthDays(cfgB);
        if (setA.isEmpty() || setB.isEmpty()) {
            return true;
        }
        return setA.stream().anyMatch(setB::contains);
    }

    private LocalDateTime nextRunTime(SprayScheduleTask t, LocalDateTime now) {
        LocalTime start = t.getStartTime();
        if (SprayConstants.CYCLE_DAILY.equals(t.getCycleType())) {
            LocalDateTime today = now.with(start);
            return today.isAfter(now) ? today : today.plusDays(1);
        }
        if (SprayConstants.CYCLE_WEEKLY.equals(t.getCycleType())) {
            Set<String> days = SprayUtil.parseWeekdays(t.getCycleConfig());
            LocalDate d = now.toLocalDate();
            for (int i = 0; i < 7; i++) {
                if (days.isEmpty() || days.contains(SprayUtil.weekdayShort(d.getDayOfWeek()))) {
                    LocalDateTime cand = d.atTime(start);
                    if (!cand.isBefore(now)) {
                        return cand;
                    }
                }
                d = d.plusDays(1);
            }
            return d.atTime(start);
        }
        if (SprayConstants.CYCLE_MONTHLY.equals(t.getCycleType())) {
            Set<Integer> days = SprayUtil.parseMonthDays(t.getCycleConfig());
            LocalDate d = now.toLocalDate();
            for (int i = 0; i < 400; i++) {
                if (days.isEmpty() || days.contains(d.getDayOfMonth())) {
                    LocalDateTime cand = d.atTime(start);
                    if (!cand.isBefore(now)) {
                        return cand;
                    }
                }
                d = d.plusDays(1);
            }
            return d.atTime(start);
        }
        return now.with(start);
    }

    // ======================== CSV 工具 ========================

    private static String csvCell(Object o) {
        if (o == null) {
            return "";
        }
        String s = o.toString().replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
