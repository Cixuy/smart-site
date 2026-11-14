package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.ScreenConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScreenConfigMapper extends BaseMapper<ScreenConfig> {
    @Update("UPDATE screen_config SET is_default=0 WHERE project_id=#{projectId} AND id<>#{screenId}")
    int clearProjectDefault(@Param("projectId") Long projectId, @Param("screenId") Long screenId);

    @Select("SELECT COUNT(*) FROM screen_config WHERE id=#{screenId} AND project_id=#{projectId} AND status=1")
    long countActiveByProject(@Param("screenId") Long screenId, @Param("projectId") Long projectId);
}
