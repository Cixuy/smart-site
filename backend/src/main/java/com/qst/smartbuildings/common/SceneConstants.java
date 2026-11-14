package com.qst.smartbuildings.common;

import com.qst.smartbuildings.common.AuthPrincipal;

/**
 * 3D可视化场景模块常量与角色判断。
 */
public final class SceneConstants {

    private SceneConstants() {}

    /** 图标类型：1-塔吊 */
    public static final int ICON_TOWER_CRANE = 1;
    /** 图标类型：2-升降机 */
    public static final int ICON_HOIST = 2;
    /** 图标类型：3-摄像头 */
    public static final int ICON_CAMERA = 3;
    /** 图标类型：4-环境监测点 */
    public static final int ICON_ENV_MONITOR = 4;
    /** 图标类型：5-喷淋设备 */
    public static final int ICON_SPRAY = 5;

    /** 告警级别：预警 */
    public static final String ALARM_LEVEL_WARNING = "warning";
    /** 告警级别：警报 */
    public static final String ALARM_LEVEL_ALARM = "alarm";
    /** 告警级别：控制 */
    public static final String ALARM_LEVEL_CONTROL = "control";

    /** 视频叠加最多同时开启路数 */
    public static final int MAX_VIDEO_OVERLAY = 4;

    /**
     * 是否为系统管理员：roleCode 含 ADMIN（与现有喷淋模块约定一致）。
     */
    public static boolean isSystemAdmin(AuthPrincipal p) {
        if (p == null || p.getRoles() == null) {
            return false;
        }
        return p.getRoles().stream()
                .anyMatch(r -> r != null && r.toUpperCase().contains("ADMIN"));
    }
}
