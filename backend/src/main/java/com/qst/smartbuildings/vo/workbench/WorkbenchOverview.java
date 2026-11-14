package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.util.List;

/**
 * 工作台首页概览
 */
@Data
public class WorkbenchOverview {

    /** 项目ID */
    private Long projectId;

    /** 安全态势统计 */
    private SafetyStats stats;

    /** 关键指标快捷入口 */
    private List<ShortcutEntry> shortcuts;

    /** 最近5条未处置告警 */
    private List<AlarmSummary> latestAlarms;

    /** 项目经理/领导可见数据（非该角色为 null） */
    private ManagerPanel managerPanel;

    /** 安全管理员可见数据（非该角色为 null） */
    private SafetyAdminPanel safetyAdminPanel;
}
