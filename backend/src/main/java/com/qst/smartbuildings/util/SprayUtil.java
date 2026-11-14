package com.qst.smartbuildings.util;

import com.alibaba.fastjson2.JSONArray;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

/**
 * 喷淋降尘模块工具方法
 * <p>调度器（SprayScheduler）与业务实现（SprayServiceImpl）共用，避免重复定义。</p>
 */
public final class SprayUtil {

    private SprayUtil() {
    }

    public static String weekdayShort(DayOfWeek dw) {
        return switch (dw) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    public static Set<String> parseWeekdays(String cfg) {
        Set<String> set = new HashSet<>();
        if (cfg == null || cfg.isBlank()) {
            return set;
        }
        try {
            JSONArray arr = JSONArray.parseArray(cfg);
            for (int i = 0; i < arr.size(); i++) {
                set.add(arr.getString(i).toUpperCase());
            }
        } catch (Exception ignored) {
            // 配置非法时视为空集合，由调用方按"未配置"处理
        }
        return set;
    }

    public static Set<Integer> parseMonthDays(String cfg) {
        Set<Integer> set = new HashSet<>();
        if (cfg == null || cfg.isBlank()) {
            return set;
        }
        try {
            JSONArray arr = JSONArray.parseArray(cfg);
            for (int i = 0; i < arr.size(); i++) {
                set.add(arr.getInteger(i));
            }
        } catch (Exception ignored) {
            // 配置非法时视为空集合，由调用方按"未配置"处理
        }
        return set;
    }
}
