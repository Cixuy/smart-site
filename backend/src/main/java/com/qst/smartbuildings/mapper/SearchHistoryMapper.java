package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.SearchHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局搜索记录数据访问层
 */
@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {
}