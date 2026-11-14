package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.common.UserContext;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.NotificationStreamService;
import com.qst.smartbuildings.service.WorkbenchService;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.workbench.AlarmSummaryListData;
import com.qst.smartbuildings.vo.workbench.CountResult;
import com.qst.smartbuildings.vo.workbench.NotificationVO;
import com.qst.smartbuildings.vo.workbench.OperationResult;
import com.qst.smartbuildings.vo.workbench.SearchHistoryListData;
import com.qst.smartbuildings.vo.workbench.SearchResultListData;
import com.qst.smartbuildings.vo.workbench.TodoAlarmListData;
import com.qst.smartbuildings.vo.workbench.WorkbenchOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工作台相关接口
 */
@Tag(name = "工作台", description = "工作台首页概览、告警滚动、通知、全局搜索等")
@RestController
@RequestMapping("/workbench")
@Validated
public class WorkbenchController {

    @Autowired
    private WorkbenchService workbenchService;

    @Autowired
    private NotificationStreamService notificationStreamService;

    /**
     * 查询当前用户搜索历史
     *
     * @return 搜索历史列表（最近10条）
     */
    @Operation(summary = "查询当前用户搜索历史",
            description = "返回当前登录用户最近 10 条全局搜索历史，按搜索时间倒序排列。")
    @GetMapping("/search-history/list")
    public Result<SearchHistoryListData> listSearchHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        return Result.ok(workbenchService.listSearchHistory(userId));
    }

    /**
     * 清空当前用户搜索历史
     *
     * @return 清空操作结果
     */
    @Operation(summary = "清空当前用户搜索历史",
            description = "删除当前登录用户的全部全局搜索历史记录。")
    @DeleteMapping("/search-history/clear")
    public Result<OperationResult> clearSearchHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        OperationResult data = new OperationResult();
        data.setSuccess(workbenchService.clearSearchHistory(userId));
        return Result.ok(data);
    }

    /**
     * 查询工作台首页概览
     *
     * @param projectId 项目ID
     * @return 工作台首页概览
     */
    @Operation(summary = "查询工作台首页概览",
            description = "返回在线设备数、离线设备数、当日告警、未处理告警、快捷入口、最新告警及角色差异化卡片数据。")
    @GetMapping("/overview/get")
    public Result<WorkbenchOverview> getWorkbenchOverview(
            @RequestParam Long projectId) {
        return Result.ok(workbenchService.getOverview(projectId, null));
    }

    /**
     * 查询最新未处理告警滚动列表
     *
     * @param projectId 项目ID
     * @param limit     返回条数（1-20，默认5）
     * @return 告警摘要滚动列表
     */
    @Operation(summary = "查询最新未处理告警滚动列表",
            description = "返回指定项目的最新未处理告警（status=0），按告警时间倒序排列，支持分页条数控制（1-20条）。")
    @GetMapping("/alarm/list-latest")
    public Result<AlarmSummaryListData> listLatestAlarms(
            @RequestParam Long projectId,
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        return Result.ok(workbenchService.listLatestAlarms(projectId, limit));
    }

    /**
     * 查询安全管理员待办告警
     *
     * @param projectId 项目ID
     * @param limit     返回条数（1-50，默认5）
     * @return 待办告警列表
     */
    @Operation(summary = "查询安全管理员待办告警",
            description = "按告警级别和发生时间排序，返回指定项目待处置告警（status=0或1），支持分页条数控制（1-50条）。")
    @GetMapping("/alarm/list-todo")
    public Result<TodoAlarmListData> listTodoAlarms(
            @RequestParam Long projectId,
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        return Result.ok(workbenchService.listTodoAlarms(projectId, limit));
    }

    /**
     * 全局搜索
     *
     * @param keyword    搜索关键字（必填，最大64字符）
     * @param searchType 搜索类型：1设备，2告警，3用户；为空时搜索全部
     * @return 搜索结果列表
     */
    @Operation(summary = "全局搜索",
            description = "按关键字搜索设备、告警、用户，支持按类型筛选，返回搜索结果列表。")
    @GetMapping("/search/query")
    public Result<SearchResultListData> globalSearch(
            @RequestParam @Size(max = 64, message = "关键字长度不能超过64") String keyword,
            @RequestParam(required = false) Integer searchType) {
        Long userId = UserContext.getUserId();
        return Result.ok(workbenchService.globalSearch(keyword, searchType, userId));
    }

    /**
     * 分页查询当前用户通知消息
     *
     * @param pageNum  页码（默认1，最小1）
     * @param pageSize 每页条数（默认10，1-100）
     * @param type     通知类型：1-告警，2-系统公告，3-任务分配，4-审批；为空查询全部
     * @param isRead   是否已读；为空查询全部
     * @return 通知分页数据
     */
    @Operation(summary = "分页查询通知消息",
            description = "分页查询当前用户的通知消息（含全体通知），支持按类型、已读状态筛选，按创建时间倒序。")
    @GetMapping("/notification/page")
    public Result<PageResult<NotificationVO>> pageNotifications(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Boolean isRead) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        return Result.ok(workbenchService.pageNotifications(userId, pageNum, pageSize, type, isRead));
    }

    /**
     * 查询当前用户未读通知数量
     *
     * @return 未读通知数量
     */
    @Operation(summary = "查询未读通知数量",
            description = "统计当前用户（含全体通知）的未读通知数量。")
    @GetMapping("/notification/get-unread-count")
    public Result<CountResult> getUnreadNotificationCount() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        CountResult data = new CountResult();
        data.setCount(workbenchService.getUnreadNotificationCount(userId));
        return Result.ok(data);
    }

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知ID
     * @return 标记操作结果
     */
    @Operation(summary = "标记单条通知为已读",
            description = "将当前用户（含全体通知）的指定通知标记为已读，并记录阅读时间。")
    @PutMapping("/notification/mark-read/{notificationId}")
    public Result<OperationResult> markNotificationRead(@PathVariable Long notificationId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        OperationResult data = new OperationResult();
        data.setSuccess(workbenchService.markNotificationRead(userId, notificationId));
        return Result.ok(data);
    }

    /**
     * 新消息 SSE 信号流
     *
     * <p>建立 SSE 长连接，服务端产生新的未读通知时推送 {@code event: new-message}
     * / {@code data: {"hasNew":true}}。连接断开后由客户端 EventSource 自动重连。</p>
     *
     * @return SSE 连接
     */
    @Operation(summary = "新消息SSE信号流",
            description = "建立 SSE 长连接，服务端在产生新的未读消息时推送 hasNew 信号。")
    @GetMapping(value = "/notification/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        return notificationStreamService.subscribe();
    }
}
