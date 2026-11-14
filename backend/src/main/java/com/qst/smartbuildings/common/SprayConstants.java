package com.qst.smartbuildings.common;

import java.math.BigDecimal;

/**
 * 喷淋降尘模块常量（触发方式 / 操作类型 / 周期 / 状态 / 系统操作人等）
 * <p>集中管理散落在调度器与业务实现中的魔法串，避免硬编码与重复定义。</p>
 */
public final class SprayConstants {

    private SprayConstants() {
    }

    /** 触发方式 */
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_AUTO = "auto";
    public static final String TRIGGER_SCHEDULE = "schedule";

    /** 操作类型 */
    public static final String OP_START = "start";
    public static final String OP_STOP = "stop";

    /** 执行周期 */
    public static final String CYCLE_DAILY = "daily";
    public static final String CYCLE_WEEKLY = "weekly";
    public static final String CYCLE_MONTHLY = "monthly";

    /** 定时任务状态：1-启用 0-禁用 */
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    /** 逻辑删除：0-未删 */
    public static final int NOT_DELETED = 0;

    /** 系统自动操作人 */
    public static final String AUTO_OPERATOR = "系统自动";
    public static final Long AUTO_OPERATOR_ID = 0L;

    /** 自动联动滞回系数：监测值需低于 阈值×该系数 才判定可关闭 */
    public static final BigDecimal THRESHOLD_HYSTERESIS = new BigDecimal("0.9");
}
