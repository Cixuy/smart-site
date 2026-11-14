package com.qst.smartbuildings.common.dict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典注册中心
 * <p>
 * 集中注册智慧工地平台全业务的数据字典，为下拉选择、枚举展示、校验提供统一数据源。
 * </p>
 *
 * <p>字典类型（dictType）命名规范：{模块}_{业务}.{字段}，例如：</p>
 * <ul>
 *     <li>alarm.source_type —— 告警来源</li>
 *     <li>alarm.level —— 告警级别</li>
 *     <li>notification.type —— 通知类型</li>
 *     <li>...</li>
 * </ul>
 */
public final class DictData {

    /** 单例注册表：dictType -> 有序字典条目列表 */
    private static final Map<String, List<DictItem>> DICT_MAP = new LinkedHashMap<>();

    static {
        registerAll();
    }

    private DictData() {
    }

    /**
     * 注册一个字典类型及其条目
     */
    private static void register(String dictType, List<DictItem> items) {
        DICT_MAP.put(dictType, Collections.unmodifiableList(items));
    }

    /**
     * 根据字典类型获取全部条目
     *
     * @param dictType 字典类型编码
     * @return 字典条目列表，不存在时返回空列表
     */
    public static List<DictItem> getByType(String dictType) {
        List<DictItem> items = DICT_MAP.get(dictType);
        return items == null ? new ArrayList<>() : items;
    }

    /**
     * 根据字典类型 + 字典值，获取字典标签
     *
     * @param dictType 字典类型编码
     * @param value    字典值
     * @return 字典标签；未匹配到时返回 null
     */
    public static String getLabel(String dictType, String value) {
        if (value == null) {
            return null;
        }
        for (DictItem item : getByType(dictType)) {
            if (value.equals(item.getValue())) {
                return item.getLabel();
            }
        }
        return null;
    }

    /**
     * 根据字典类型 + 字典值，获取字典标签（Integer 值重载）
     */
    public static String getLabel(String dictType, Integer value) {
        return value == null ? null : getLabel(dictType, value.toString());
    }

    /**
     * 根据字典类型 + 字典值，获取字典条目
     */
    public static DictItem getItem(String dictType, String value) {
        if (value == null) {
            return null;
        }
        for (DictItem item : getByType(dictType)) {
            if (value.equals(item.getValue())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取全部字典类型编码
     */
    public static List<String> getAllTypes() {
        return new ArrayList<>(DICT_MAP.keySet());
    }

    /**
     * 获取全部字典（dictType -> 条目列表）
     */
    public static Map<String, List<DictItem>> getAll() {
        return new LinkedHashMap<>(DICT_MAP);
    }

    // ======================== 注册全部数据字典 ========================

    private static void registerAll() {
        // -------------------- 一、用户与权限 --------------------
        register("user.status", list(
                it("user.status", "启用", "1", 1, "success"),
                it("user.status", "禁用", "0", 2, "danger"),
                it("user.status", "锁定", "2", 3, "warning")
        ));
        register("role.data_scope", list(
                it("role.data_scope", "全部", "1", 1),
                it("role.data_scope", "自定义", "2", 2),
                it("role.data_scope", "仅本人", "3", 3)
        ));
        register("menu.type", list(
                it("menu.type", "目录", "1", 1),
                it("menu.type", "菜单", "2", 2),
                it("menu.type", "按钮", "3", 3)
        ));

        // -------------------- 二、告警管理 --------------------
        register("alarm.source_type", list(
                it("alarm.source_type", "设备监测告警", "1", 1, "warning"),
                it("alarm.source_type", "环境监测告警", "2", 2, "success"),
                it("alarm.source_type", "AI识别告警", "3", 3, "info")
        ));
        register("alarm.level", list(
                it("alarm.level", "预警", "1", 1, "warning").setRemark("黄色，标红闪烁，无声音提示"),
                it("alarm.level", "警报", "2", 2, "warning").setRemark("橙色，弹窗+单次提示音"),
                it("alarm.level", "控制", "3", 3, "danger").setRemark("红色，弹窗+持续蜂鸣声")
        ));
        register("alarm.status", list(
                it("alarm.status", "未处置", "0", 1, "danger"),
                it("alarm.status", "处置中", "1", 2, "warning"),
                it("alarm.status", "已处置", "2", 3, "success")
        ));
        register("alarm.disposal_conclusion", list(
                it("alarm.disposal_conclusion", "属实", "1", 1),
                it("alarm.disposal_conclusion", "误报", "2", 2).setRemark("需填写误报原因 falseReason"),
                it("alarm.disposal_conclusion", "其他", "3", 3)
        ));
        register("alarm.push_channel", list(
                it("alarm.push_channel", "短信", "1", 1),
                it("alarm.push_channel", "企业微信", "2", 2),
                it("alarm.push_channel", "钉钉", "3", 3),
                it("alarm.push_channel", "邮件", "4", 4)
        ));
        register("alarm.push_status", list(
                it("alarm.push_status", "待推送", "0", 1, "warning"),
                it("alarm.push_status", "推送成功", "1", 2, "success"),
                it("alarm.push_status", "推送失败", "2", 3, "danger")
        ));
        // AI 告警类型编码
        register("alarm.type.ai", list(
                it("alarm.type.ai", "安全帽未佩戴", "NO_HELMET", 1),
                it("alarm.type.ai", "安全服未穿", "NO_VEST", 2),
                it("alarm.type.ai", "现场吸烟", "SMOKING", 3),
                it("alarm.type.ai", "现场明火", "FIRE", 4)
        ));
        // 设备监测告警类型编码
        register("alarm.type.device", list(
                it("alarm.type.device", "超载（升降机）", "OVERLOAD", 1),
                it("alarm.type.device", "力矩超标（塔吊）", "MOMENT_EXCEED", 2),
                it("alarm.type.device", "风速超标", "WIND_EXCEED", 3),
                it("alarm.type.device", "吊重超标（塔吊）", "LOAD_EXCEED", 4),
                it("alarm.type.device", "双门同开（升降机联锁）", "DOOR_OPEN", 5)
        ));
        // 环境监测告警类型编码
        register("alarm.type.env", list(
                it("alarm.type.env", "PM2.5超标", "PM25_EXCEED", 1),
                it("alarm.type.env", "PM10超标", "PM10_EXCEED", 2),
                it("alarm.type.env", "噪声超标", "NOISE_EXCEED", 3)
        ));

        // -------------------- 三、设备与监控 --------------------
        register("device.online_status", list(
                it("device.online_status", "离线", "0", 1, "info"),
                it("device.online_status", "在线", "1", 2, "success")
        ));
        register("tower_crane.alarm_status", list(
                it("tower_crane.alarm_status", "正常", "0", 1, "success"),
                it("tower_crane.alarm_status", "预警", "1", 2, "warning"),
                it("tower_crane.alarm_status", "报警", "2", 3, "danger")
        ));
        register("hoist.interlock_alarm", list(
                it("hoist.interlock_alarm", "正常", "0", 1, "success"),
                it("hoist.interlock_alarm", "双门同开", "1", 2, "danger"),
                it("hoist.interlock_alarm", "超载", "2", 3, "danger"),
                it("hoist.interlock_alarm", "超速", "3", 4, "danger"),
                it("hoist.interlock_alarm", "超高", "4", 5, "danger")
        ));
        register("hoist.run_direction", list(
                it("hoist.run_direction", "停止", "0", 1),
                it("hoist.run_direction", "上升", "1", 2),
                it("hoist.run_direction", "下降", "2", 3)
        ));
        register("hoist.door_status", list(
                it("hoist.door_status", "关", "0", 1),
                it("hoist.door_status", "开", "1", 2)
        ));
        register("env.data_status", list(
                it("env.data_status", "正常", "0", 1, "success"),
                it("env.data_status", "预警", "1", 2, "warning"),
                it("env.data_status", "报警", "2", 3, "danger")
        ));
        register("video.record_type", list(
                it("video.record_type", "录像", "1", 1),
                it("video.record_type", "截图", "2", 2),
                it("video.record_type", "AI告警截图", "3", 3)
        ));
        register("scene.device_icon_type", list(
                it("scene.device_icon_type", "塔吊", "1", 1),
                it("scene.device_icon_type", "升降机", "2", 2),
                it("scene.device_icon_type", "摄像头", "3", 3),
                it("scene.device_icon_type", "环境监测点", "4", 4),
                it("scene.device_icon_type", "喷淋设备", "5", 5)
        ));

        // -------------------- 四、阈值配置 --------------------
        register("threshold.compare_mode", list(
                it("threshold.compare_mode", "数值直接比较", "1", 1).setRemark("当前值与阈值上下限比较"),
                it("threshold.compare_mode", "按额定值百分比比较", "2", 2).setRemark("当前值/额定值×100% 与阈值比较"),
                it("threshold.compare_mode", "布尔条件（离散事件）", "3", 3).setRemark("条件成立即触发，仅升降机门联锁使用")
        ));
        register("threshold.metric_type.tower", list(
                it("threshold.metric_type.tower", "吊重", "load", 1).setRemark("单位 kN"),
                it("threshold.metric_type.tower", "力矩", "moment", 2).setRemark("单位 kN·m"),
                it("threshold.metric_type.tower", "力矩百分比", "moment_percent", 3).setRemark("单位 %"),
                it("threshold.metric_type.tower", "风速", "wind_speed", 4).setRemark("单位 m/s"),
                it("threshold.metric_type.tower", "幅度", "amplitude", 5).setRemark("单位 m"),
                it("threshold.metric_type.tower", "高度", "height", 6).setRemark("单位 m")
        ));
        register("threshold.metric_type.hoist", list(
                it("threshold.metric_type.hoist", "载重百分比", "load_percent", 1).setRemark("单位 %"),
                it("threshold.metric_type.hoist", "载人数", "passenger_count", 2).setRemark("单位 人"),
                it("threshold.metric_type.hoist", "风速", "wind_speed", 3).setRemark("单位 m/s"),
                it("threshold.metric_type.hoist", "提升速度", "speed", 4).setRemark("单位 m/min"),
                it("threshold.metric_type.hoist", "提升高度", "height", 5).setRemark("单位 m"),
                it("threshold.metric_type.hoist", "双门同开联锁", "door_interlock", 6).setRemark("布尔条件")
        ));
        register("threshold.metric_type.env", list(
                it("threshold.metric_type.env", "PM2.5", "PM25", 1).setRemark("单位 μg/m³"),
                it("threshold.metric_type.env", "PM10", "PM10", 2).setRemark("单位 μg/m³"),
                it("threshold.metric_type.env", "噪声", "NOISE", 3).setRemark("单位 dB"),
                it("threshold.metric_type.env", "温度", "TEMP", 4).setRemark("单位 ℃"),
                it("threshold.metric_type.env", "湿度", "HUMIDITY", 5).setRemark("单位 %"),
                it("threshold.metric_type.env", "风速", "WIND", 6).setRemark("单位 m/s")
        ));

        // -------------------- 五、喷淋降尘 --------------------
        register("spray.trigger_mode", list(
                it("spray.trigger_mode", "自动联动", "auto", 1),
                it("spray.trigger_mode", "手动", "manual", 2),
                it("spray.trigger_mode", "定时", "schedule", 3)
        ));
        register("spray.operation_type", list(
                it("spray.operation_type", "启动", "start", 1, "success"),
                it("spray.operation_type", "关闭", "stop", 2, "danger")
        ));
        register("spray.cycle_type", list(
                it("spray.cycle_type", "每天", "daily", 1),
                it("spray.cycle_type", "每周", "weekly", 2),
                it("spray.cycle_type", "每月", "monthly", 3)
        ));

        // -------------------- 六、工作台与大屏 --------------------
        register("notification.type", list(
                it("notification.type", "告警通知", "1", 1, "danger"),
                it("notification.type", "系统公告", "2", 2, "info"),
                it("notification.type", "任务分配", "3", 3, "warning"),
                it("notification.type", "审批通知", "4", 4, "success")
        ));
        register("workbench.card_id", list(
                it("workbench.card_id", "在线设备数", "online_device", 1),
                it("workbench.card_id", "离线设备数", "offline_device", 2),
                it("workbench.card_id", "当日告警总数", "daily_alarm", 3),
                it("workbench.card_id", "未处理告警数", "pending_alarm", 4),
                it("workbench.card_id", "塔吊运行状态", "tower_crane", 5),
                it("workbench.card_id", "升降机运行状态", "hoist", 6),
                it("workbench.card_id", "环境空气质量", "environment", 7),
                it("workbench.card_id", "最新告警滚动栏", "alarm_scroll", 8),
                it("workbench.card_id", "本周告警趋势图", "alarm_trend", 9),
                it("workbench.card_id", "设备在线率统计", "online_rate", 10),
                it("workbench.card_id", "项目进度概览", "project_progress", 11),
                it("workbench.card_id", "待办告警列表", "todo_alarm", 12),
                it("workbench.card_id", "今日待巡检设备", "patrol_task", 13),
                it("workbench.card_id", "今日喷淋操作记录", "spray_record", 14),
                it("workbench.card_id", "我的处置统计", "disposal_stats", 15)
        ));

        // -------------------- 七、智能体 --------------------
        register("agent.intent_type", list(
                it("agent.intent_type", "安全态势问答", "safety_query", 1).setRemark("例：当前工地安全状况如何？"),
                it("agent.intent_type", "告警智能分析", "alarm_analysis", 2).setRemark("例：分析今日塔吊告警原因"),
                it("agent.intent_type", "设备健康诊断", "device_diagnosis", 3).setRemark("例：检查1号塔吊健康状况"),
                it("agent.intent_type", "安全建议生成", "suggestion", 4).setRemark("例：生成本周安全管理建议")
        ));

        // -------------------- 八、其他通用状态 --------------------
        register("common.enable_status", list(
                it("common.enable_status", "禁用", "0", 1, "danger"),
                it("common.enable_status", "启用", "1", 2, "success")
        ));
        register("project.status", list(
                it("project.status", "进行中", "1", 1, "success"),
                it("project.status", "已竣工", "2", 2, "info"),
                it("project.status", "已停工", "3", 3, "warning")
        ));
        register("sensor.status", list(
                it("sensor.status", "运行", "1", 1, "success"),
                it("sensor.status", "停止", "0", 2, "warning"),
                it("sensor.status", "故障", "2", 3, "danger")
        ));
        register("search.search_type", list(
                it("search.search_type", "设备", "1", 1),
                it("search.search_type", "告警", "2", 2),
                it("search.search_type", "用户", "3", 3)
        ));
        register("sys.oper_type", list(
                it("sys.oper_type", "新增", "ADD", 1),
                it("sys.oper_type", "修改", "UPDATE", 2),
                it("sys.oper_type", "删除", "DELETE", 3)
        ));
        register("sys.oper_status", list(
                it("sys.oper_status", "成功", "0", 1, "success"),
                it("sys.oper_status", "失败", "1", 2, "danger")
        ));
        register("login.status", list(
                it("login.status", "成功", "0", 1, "success"),
                it("login.status", "失败", "1", 2, "danger"),
                it("login.status", "锁定", "2", 3, "warning")
        ));
    }

    // ======================== 便捷构造方法 ========================

    private static List<DictItem> list(DictItem... items) {
        List<DictItem> list = new ArrayList<>();
        Collections.addAll(list, items);
        return list;
    }

    private static DictItem it(String dictType, String label, String value, Integer sort) {
        DictItem item = new DictItem(dictType, label, value, sort);
        item.setValueType("string");
        return item;
    }

    private static DictItem it(String dictType, String label, String value, Integer sort, String tagType) {
        DictItem item = it(dictType, label, value, sort);
        item.setTagType(tagType);
        return item;
    }
}
