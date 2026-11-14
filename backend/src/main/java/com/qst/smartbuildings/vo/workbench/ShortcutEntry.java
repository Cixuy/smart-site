package com.qst.smartbuildings.vo.workbench;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关键指标快捷入口
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortcutEntry {

    /** 入口标识（对应卡片组件 card_id） */
    private String key;

    /** 标题 */
    private String title;

    /** 展示值 */
    private String value;

    /** 跳转路由 */
    private String targetUrl;
}
