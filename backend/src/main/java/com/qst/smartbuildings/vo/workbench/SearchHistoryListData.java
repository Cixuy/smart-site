package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.util.List;

/**
 * 全局搜索历史列表数据
 */
@Data
public class SearchHistoryListData {

    /** 搜索历史列表（最近10条） */
    private List<SearchHistoryVO> records;
}