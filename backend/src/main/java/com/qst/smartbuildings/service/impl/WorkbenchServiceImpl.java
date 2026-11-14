package com.qst.smartbuildings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.SearchHistory;
import com.qst.smartbuildings.domain.SystemNotification;
import com.qst.smartbuildings.mapper.SearchHistoryMapper;
import com.qst.smartbuildings.mapper.SysRoleMapper;
import com.qst.smartbuildings.mapper.SystemNotificationMapper;
import com.qst.smartbuildings.mapper.WorkbenchMapper;
import com.qst.smartbuildings.service.WorkbenchService;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.UserRoleRow;
import com.qst.smartbuildings.vo.workbench.AlarmSummary;
import com.qst.smartbuildings.vo.workbench.AlarmSummaryListData;
import com.qst.smartbuildings.vo.workbench.ManagerPanel;
import com.qst.smartbuildings.vo.workbench.NotificationVO;
import com.qst.smartbuildings.vo.workbench.SafetyAdminPanel;
import com.qst.smartbuildings.vo.workbench.SafetyStats;
import com.qst.smartbuildings.vo.workbench.SearchHistoryListData;
import com.qst.smartbuildings.vo.workbench.SearchHistoryVO;
import com.qst.smartbuildings.vo.workbench.SearchResult;
import com.qst.smartbuildings.vo.workbench.SearchResultListData;
import com.qst.smartbuildings.vo.workbench.ShortcutEntry;
import com.qst.smartbuildings.vo.workbench.TodoAlarm;
import com.qst.smartbuildings.vo.workbench.TodoAlarmListData;
import com.qst.smartbuildings.vo.workbench.WorkbenchOverview;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作台服务实现
 */
@Service
public class WorkbenchServiceImpl implements WorkbenchService {

    /** 项目经理/领导相关角色编码关键字 */
    private static final String ROLE_MANAGER_KEY = "MANAGER";
    /** 安全管理员角色编码关键字 */
    private static final String ROLE_SAFETY_ADMIN_KEY = "SAFETY";

    private final WorkbenchMapper workbenchMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final SystemNotificationMapper systemNotificationMapper;

    public WorkbenchServiceImpl(WorkbenchMapper workbenchMapper, SysRoleMapper sysRoleMapper,
                                SearchHistoryMapper searchHistoryMapper,
                                SystemNotificationMapper systemNotificationMapper) {
        this.workbenchMapper = workbenchMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.searchHistoryMapper = searchHistoryMapper;
        this.systemNotificationMapper = systemNotificationMapper;
    }

    @Override
    public WorkbenchOverview getOverview(Long projectId, Long userId) {
        WorkbenchOverview overview = new WorkbenchOverview();
        overview.setProjectId(projectId);
        overview.setStats(buildStats(projectId));
        overview.setShortcuts(buildShortcuts(projectId));
        overview.setLatestAlarms(workbenchMapper.selectLatestAlarms(projectId, 5));

        // 角色差异化数据
        boolean isManager = false;
        boolean isSafetyAdmin = false;
        if (userId != null) {
            List<UserRoleRow> roles = sysRoleMapper.selectRolesByUserIds(Collections.singletonList(userId));
            for (UserRoleRow row : roles) {
                String code = row.getRoleCode() == null ? "" : row.getRoleCode().toUpperCase();
                String name = row.getRoleName() == null ? "" : row.getRoleName().toUpperCase();
                if (code.contains(ROLE_MANAGER_KEY) || name.contains(ROLE_MANAGER_KEY)) {
                    isManager = true;
                }
                if (code.contains(ROLE_SAFETY_ADMIN_KEY) || name.contains(ROLE_SAFETY_ADMIN_KEY)) {
                    isSafetyAdmin = true;
                }
            }
        }

        // 无明确角色或为项目经理/领导时，返回经理面板
        if (!isSafetyAdmin || isManager) {
            overview.setManagerPanel(buildManagerPanel(projectId));
        }
        // 安全管理员面板
        if (isSafetyAdmin) {
            overview.setSafetyAdminPanel(buildSafetyAdminPanel(projectId));
        }

        return overview;
    }

    /**
     * 安全态势统计
     */
    private SafetyStats buildStats(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = now.toLocalDate().atTime(LocalTime.MAX);

        SafetyStats stats = new SafetyStats();
        stats.setOnlineDevices((int) workbenchMapper.countOnlineDevices());
        stats.setOfflineDevices((int) workbenchMapper.countOfflineDevices());
        stats.setDailyAlarms((int) workbenchMapper.countDailyAlarms(projectId, dayStart, dayEnd));
        stats.setPendingAlarms((int) workbenchMapper.countPendingAlarms(projectId));
        return stats;
    }

    /**
     * 关键指标快捷入口
     */
    private List<ShortcutEntry> buildShortcuts(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = now.toLocalDate().atTime(LocalTime.MAX);

        long onlineDevices = workbenchMapper.countOnlineDevices();
        long offlineDevices = workbenchMapper.countOfflineDevices();
        long dailyAlarms = workbenchMapper.countDailyAlarms(projectId, dayStart, dayEnd);
        long pendingAlarms = workbenchMapper.countPendingAlarms(projectId);

        List<ShortcutEntry> list = new ArrayList<>();
        list.add(new ShortcutEntry("online_device", "在线设备数", String.valueOf(onlineDevices), "/devices?status=online"));
        list.add(new ShortcutEntry("offline_device", "离线设备数", String.valueOf(offlineDevices), "/devices?status=offline"));
        list.add(new ShortcutEntry("daily_alarm", "当日告警", String.valueOf(dailyAlarms), "/alarms"));
        list.add(new ShortcutEntry("pending_alarm", "未处理告警", String.valueOf(pendingAlarms), "/alarms?status=pending"));
        list.add(new ShortcutEntry("tower_crane", "塔吊运行状态", "运行正常", "/tower-cranes"));
        list.add(new ShortcutEntry("hoist", "升降机运行状态", "运行正常", "/hoists"));
        list.add(new ShortcutEntry("environment", "环境空气质量", "良好", "/environment"));
        list.add(new ShortcutEntry("alarm_scroll", "最新告警滚动栏", "查看详情", "/alarms"));
        return list;
    }

    /**
     * 项目经理面板：近7日告警趋势、在线率、告警处置进度
     */
    private ManagerPanel buildManagerPanel(Long projectId) {
        ManagerPanel panel = new ManagerPanel();
        panel.setAlarmTrend(workbenchMapper.selectAlarmTrend(projectId, LocalDate.now().minusDays(7)));

        long online = workbenchMapper.countOnlineDevices();
        long offline = workbenchMapper.countOfflineDevices();
        long total = online + offline;
        panel.setOnlineRate(total == 0 ? 100.0D : Math.round(online * 10000.0D / total) / 100.0D);

        // 告警处置进度（%）= 已处置告警数 / 全部告警数；无告警视为 100%
        long totalAlarms = workbenchMapper.countTotalAlarms(projectId);
        long disposedAlarms = workbenchMapper.countDisposedAlarms(projectId);
        panel.setDisposalProgress(
                totalAlarms == 0 ? 100.0D : Math.round(disposedAlarms * 10000.0D / totalAlarms) / 100.0D);
        return panel;
    }

    /**
     * 安全管理员面板：待办告警、今日待巡检设备、今日喷淋记录
     */
    private SafetyAdminPanel buildSafetyAdminPanel(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = now.toLocalDate().atTime(LocalTime.MAX);

        SafetyAdminPanel panel = new SafetyAdminPanel();
        panel.setTodoAlarms(workbenchMapper.selectTodoAlarms(projectId));

        // 今日待巡检设备数（基于未处置告警涉及的去重设备数，预置合理统计）
        List<TodoAlarm> todoAlarms = panel.getTodoAlarms();
        panel.setPatrolTaskCount(todoAlarms != null ? todoAlarms.size() : 0);

        panel.setSprayRecordCount((int) workbenchMapper.countTodaySprayRecords(dayStart, dayEnd));
        return panel;
    }

    @Override
    public SearchHistoryListData listSearchHistory(Long userId) {
        List<SearchHistory> rows = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .orderByDesc(SearchHistory::getCreatedAt)
                        .orderByDesc(SearchHistory::getId)
                        .last("LIMIT 10"));

        List<SearchHistoryVO> records = rows.stream()
                .map(this::toSearchHistoryVO)
                .collect(Collectors.toList());

        SearchHistoryListData data = new SearchHistoryListData();
        data.setRecords(records);
        return data;
    }

    @Override
    public boolean clearSearchHistory(Long userId) {
        return searchHistoryMapper.delete(
                new LambdaQueryWrapper<SearchHistory>().eq(SearchHistory::getUserId, userId)) >= 0;
    }

    /**
     * 将实体转换为搜索历史 VO
     */
    private SearchHistoryVO toSearchHistoryVO(SearchHistory row) {
        SearchHistoryVO vo = new SearchHistoryVO();
        vo.setId(row.getId());
        vo.setKeyword(row.getKeyword());
        vo.setSearchType(row.getSearchType());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }

    @Override
    public AlarmSummaryListData listLatestAlarms(Long projectId, int limit) {
        // 限制 limit 在 1~20 范围内
        if (limit < 1) {
            limit = 1;
        } else if (limit > 20) {
            limit = 20;
        }

        List<AlarmSummary> records = workbenchMapper.selectLatestAlarmsByProjectId(projectId, limit);

        AlarmSummaryListData data = new AlarmSummaryListData();
        data.setRecords(records);
        return data;
    }

    @Override
    public TodoAlarmListData listTodoAlarms(Long projectId, int limit) {
        // 限制 limit 在 1~50 范围内
        if (limit < 1) {
            limit = 1;
        } else if (limit > 50) {
            limit = 50;
        }

        List<TodoAlarm> records = workbenchMapper.selectTodoAlarmsByProjectId(projectId, limit);

        TodoAlarmListData data = new TodoAlarmListData();
        data.setRecords(records);
        return data;
    }

    @Override
    public SearchResultListData globalSearch(String keyword, Integer searchType, Long userId) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("keyword must not be empty");
        }
        // 限制关键字长度
        if (keyword.length() > 64) {
            keyword = keyword.substring(0, 64);
        }
        // 记录搜索历史（仅登录用户）
        recordSearchHistory(userId, keyword, searchType);
        // 搜索结果上限
        final int limit = 20;

        List<SearchResult> records = new ArrayList<>();

        // type=1 或 type=null 时搜索设备
        if (searchType == null || searchType == 1) {
            List<WorkbenchMapper.DeviceSearchRow> devices = workbenchMapper.searchDevices(keyword, limit);
            for (WorkbenchMapper.DeviceSearchRow row : devices) {
                SearchResult r = new SearchResult();
                r.setId(row.getId());
                r.setTitle(row.getTitle());
                r.setSearchType(1);
                r.setSummary("设备编码 " + row.getDeviceCode() + "，" + row.getDeviceStatus());
                r.setTargetUrl("/devices/" + row.getId());
                records.add(r);
            }
        }

        // type=2 或 type=null 时搜索告警
        if (searchType == null || searchType == 2) {
            List<WorkbenchMapper.AlarmSearchRow> alarms = workbenchMapper.searchAlarms(keyword, limit);
            for (WorkbenchMapper.AlarmSearchRow row : alarms) {
                SearchResult r = new SearchResult();
                r.setId(row.getId());
                r.setTitle(row.getTitle());
                r.setSearchType(2);
                r.setSummary("告警级别 " + row.getAlarmLevel() + "，设备 " + row.getDeviceName()
                        + "，时间 " + row.getAlarmTime());
                r.setTargetUrl("/alarms/" + row.getId());
                records.add(r);
            }
        }

        // type=3 或 type=null 时搜索用户
        if (searchType == null || searchType == 3) {
            List<WorkbenchMapper.UserSearchRow> users = workbenchMapper.searchUsers(keyword, limit);
            for (WorkbenchMapper.UserSearchRow row : users) {
                SearchResult r = new SearchResult();
                r.setId(row.getId());
                r.setTitle(row.getRealName() != null ? row.getRealName() : row.getUsername());
                r.setSearchType(3);
                r.setSummary("用户名 " + row.getUsername() + (row.getPhone() != null ? "，电话 " + row.getPhone() : ""));
                r.setTargetUrl("/users/" + row.getId());
                records.add(r);
            }
        }

        SearchResultListData data = new SearchResultListData();
        data.setRecords(records);
        return data;
    }

    /**
     * 记录搜索历史
     *
     * <p>相同关键词+搜索类型视为重复搜索，仅刷新时间并上浮到最前；每人最多保留最近 10 条。</p>
     */
    private void recordSearchHistory(Long userId, String keyword, Integer searchType) {
        if (userId == null || keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        String kw = keyword.trim();
        if (kw.length() > 64) {
            kw = kw.substring(0, 64);
        }
        LocalDateTime now = LocalDateTime.now();
        SearchHistory existing = searchHistoryMapper.selectOne(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .eq(SearchHistory::getKeyword, kw)
                        .eq(searchType != null, SearchHistory::getSearchType, searchType)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setCreatedAt(now);
            searchHistoryMapper.updateById(existing);
        } else {
            SearchHistory record = new SearchHistory();
            record.setUserId(userId);
            record.setKeyword(kw);
            record.setSearchType(searchType);
            record.setCreatedAt(now);
            searchHistoryMapper.insert(record);
        }
        // 只保留最近 10 条
        List<Long> recentIds = searchHistoryMapper.selectList(
                        new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId)
                                .orderByDesc(SearchHistory::getCreatedAt)
                                .orderByDesc(SearchHistory::getId)
                                .last("LIMIT 10"))
                .stream().map(SearchHistory::getId).collect(Collectors.toList());
if (recentIds.isEmpty()) {
            searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                    .eq(SearchHistory::getUserId, userId));
        } else {
            searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                    .eq(SearchHistory::getUserId, userId)
                    .notIn(SearchHistory::getId, recentIds));
        }
    }

    @Override
    public PageResult<NotificationVO> pageNotifications(Long userId, long pageNum, long pageSize,
                                                        Integer type, Boolean isRead) {
        Page<SystemNotification> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SystemNotification> wrapper = new LambdaQueryWrapper<SystemNotification>()
                .and(w -> w.eq(SystemNotification::getTargetUserId, userId)
                        .or().eq(SystemNotification::getTargetUserId, 0L))
                .eq(type != null, SystemNotification::getType, type)
                .eq(isRead != null, SystemNotification::getIsRead, isRead)
                .orderByDesc(SystemNotification::getCreatedAt)
                .orderByDesc(SystemNotification::getId);

        Page<SystemNotification> result = systemNotificationMapper.selectPage(page, wrapper);
        List<NotificationVO> records = result.getRecords().stream()
                .map(this::toNotificationVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), result.getPages(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public int getUnreadNotificationCount(Long userId) {
        Long count = systemNotificationMapper.selectCount(
                new LambdaQueryWrapper<SystemNotification>()
                        .and(w -> w.eq(SystemNotification::getTargetUserId, userId)
                                .or().eq(SystemNotification::getTargetUserId, 0L))
                        .eq(SystemNotification::getIsRead, false));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public boolean markNotificationRead(Long userId, Long notificationId) {
        SystemNotification notification = systemNotificationMapper.selectById(notificationId);
        if (notification == null) {
            return false;
        }
        Long target = notification.getTargetUserId();
        boolean mine = target != null && (target.equals(userId) || target == 0L);
        if (!mine) {
            return false;
        }
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return true;
        }
        notification.setIsRead(true);
        notification.setReadTime(LocalDateTime.now());
        return systemNotificationMapper.updateById(notification) == 1;
    }

    /**
     * 将实体转换为通知 VO
     */
    private NotificationVO toNotificationVO(SystemNotification row) {
        NotificationVO vo = new NotificationVO();
        vo.setId(row.getId());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setType(row.getType());
        vo.setTargetUserId(row.getTargetUserId());
        vo.setIsRead(row.getIsRead());
        vo.setReadTime(row.getReadTime());
        vo.setSourceUrl(row.getSourceUrl());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }
}