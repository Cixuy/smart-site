package com.qst.smartbuildings.vo.workbench;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 全局搜索结果项
 */
@Data
@Schema(description = "搜索结果项")
public class SearchResult {

    @Schema(description = "结果ID")
    private Long id;

    @Schema(description = "标题", example = "1号塔吊")
    private String title;

    @Schema(description = "搜索类型：1设备，2告警，3用户", example = "1")
    private Integer searchType;

    @Schema(description = "摘要描述", example = "设备编码 TC-001，当前在线")
    private String summary;

    @Schema(description = "目标URL", example = "/devices/2001")
    private String targetUrl;
}