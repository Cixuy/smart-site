package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局搜索记录（search_history）
 *
 * <p>记录用户在顶部全局搜索框的搜索历史，同一用户保留最近 10 条（由应用层控制）。</p>
 */
@Data
@TableName("search_history")
public class SearchHistory {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 搜索关键词 */
    private String keyword;

    /** 搜索类型：1-设备，2-告警，3-用户 */
    private Integer searchType;

    /** 搜索时间 */
    private LocalDateTime createdAt;
}