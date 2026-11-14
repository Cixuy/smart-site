package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qst.smartbuildings.domain.ScreenVideoRotation;
import com.qst.smartbuildings.vo.ScreenVideoRotationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScreenVideoRotationMapper extends BaseMapper<ScreenVideoRotation> {
    @Select("SELECT r.id, r.screen_id AS screenId, r.camera_id AS cameraId, " +
            "c.camera_name AS cameraName, c.stream_url AS streamUrl, " +
            "r.display_order AS displayOrder, r.duration " +
            "FROM screen_video_rotation r JOIN biz_camera c ON c.id=r.camera_id " +
            "WHERE r.screen_id=#{screenId} AND c.is_deleted=0 ORDER BY r.display_order ASC, r.id ASC")
    List<ScreenVideoRotationVO> selectRotationVOs(@Param("screenId") Long screenId);
}
