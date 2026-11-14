package com.qst.smartbuildings.vo.workbench;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 全局搜索结果列表数据
 */
@Data
@Schema(description = "搜索结果列表数据")
public class SearchResultListData {

    @Schema(description = "搜索结果列表")
    private List<SearchResult> records;
}