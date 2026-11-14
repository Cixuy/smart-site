package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局搜索历史记录
 */
@Data
public class SearchHistoryVO {

    /** 记录ID */
    private Long id;

    /** 搜索关键词 */
    private String keyword;

    /** 搜索类型：1-设备，2-告警，3-用户 */
    private Integer searchType;

    /** 搜索时间 */
    private LocalDateTime createdAt;
}