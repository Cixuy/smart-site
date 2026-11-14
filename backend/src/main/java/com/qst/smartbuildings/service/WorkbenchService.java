package com.qst.smartbuildings.service;

import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.workbench.AlarmSummaryListData;
import com.qst.smartbuildings.vo.workbench.NotificationVO;
import com.qst.smartbuildings.vo.workbench.SearchHistoryListData;
import com.qst.smartbuildings.vo.workbench.SearchResultListData;
import com.qst.smartbuildings.vo.workbench.TodoAlarmListData;
import com.qst.smartbuildings.vo.workbench.WorkbenchOverview;

/**
 * 工作台服务
 */
public interface WorkbenchService {

    /**
     * 查询工作台首页概览
     *
     * @param projectId 项目ID
     * @param userId    当前登录用户ID（用于角色差异化数据，可为空）
     * @return 工作台首页概览
     */
    WorkbenchOverview getOverview(Long projectId, Long userId);

    /**
     * 查询最新未处理告警滚动列表
     *
     * @param projectId 项目ID
     * @param limit     返回条数（1-20，默认5）
     * @return 告警摘要滚动列表数据
     */
    AlarmSummaryListData listLatestAlarms(Long projectId, int limit);

    /**
     * 查询安全管理员待办告警
     *
     * @param projectId 项目ID
     * @param limit     返回条数（1-50，默认5）
     * @return 待办告警列表数据
     */
    TodoAlarmListData listTodoAlarms(Long projectId, int limit);

    /**
     * 全局搜索
     *
     * <p>执行搜索后记录搜索历史（当前用户保留最近10条）。</p>
     *
     * @param keyword    搜索关键字
     * @param searchType 搜索类型：1设备，2告警，3用户；为空表示全部
     * @param userId     当前登录用户ID（用于记录搜索历史，可为空则不记录）
     * @return 搜索结果列表数据
     */
    SearchResultListData globalSearch(String keyword, Integer searchType, Long userId);

    /**
     * 查询当前用户搜索历史
     *
     * @param userId 当前登录用户ID
     * @return 搜索历史列表数据（最近10条，按时间倒序）
     */
    SearchHistoryListData listSearchHistory(Long userId);

    /**
     * 清空当前用户搜索历史
     *
     * @param userId 当前登录用户ID
     * @return 是否清空成功
     */
    boolean clearSearchHistory(Long userId);

    /**
     * 分页查询当前用户通知消息
     *
     * <p>返回发给当前用户（target_user_id = userId）及全体（target_user_id = 0）的通知，按创建时间倒序。</p>
     *
     * @param userId   当前登录用户ID
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @param type     通知类型：1-告警，2-系统公告，3-任务分配，4-审批；为空表示全部
     * @param isRead   是否已读；为空表示全部
     * @return 通知分页数据
     */
    PageResult<NotificationVO> pageNotifications(Long userId, long pageNum, long pageSize,
                                                 Integer type, Boolean isRead);

    /**
     * 查询当前用户未读通知数量
     *
     * <p>统计发给当前用户及全体的未读通知数。</p>
     *
     * @param userId 当前登录用户ID
     * @return 未读通知数量
     */
    int getUnreadNotificationCount(Long userId);

    /**
     * 标记单条通知为已读
     *
     * <p>仅允许标记发给当前用户或全体的通知；通知不存在或无权访问时返回 false。</p>
     *
     * @param userId         当前登录用户ID
     * @param notificationId 通知ID
     * @return 是否标记成功（已读的再次标记也视为成功）
     */
    boolean markNotificationRead(Long userId, Long notificationId);
}
