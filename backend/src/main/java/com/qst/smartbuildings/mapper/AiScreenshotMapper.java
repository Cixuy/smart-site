package com.qst.smartbuildings.mapper;

import com.qst.smartbuildings.domain.AiScreenshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiScreenshotMapper {
    @Select("SELECT screenshot_url FROM biz_ai_screenshot WHERE alarm_id=#{alarmId} ORDER BY id ASC")
    List<String> selectUrls(@Param("alarmId") Long alarmId);

    /** 查询指定 AI 告警下的全部截图记录（用于删除时定位本地文件）。 */
    @Select("SELECT id, alarm_id, screenshot_url, screenshot_type, file_size, width, height, created_at "
            + "FROM biz_ai_screenshot WHERE alarm_id=#{alarmId} ORDER BY id ASC")
    List<AiScreenshot> selectByAlarmId(@Param("alarmId") Long alarmId);

    /** 查询单条截图记录（用于删除时定位本地文件）。 */
    @Select("SELECT id, alarm_id, screenshot_url, screenshot_type, file_size, width, height, created_at "
            + "FROM biz_ai_screenshot WHERE id=#{id}")
    AiScreenshot selectById(@Param("id") Long id);

    @Delete("DELETE FROM biz_ai_screenshot WHERE alarm_id=#{alarmId}")
    int deleteByAlarmId(@Param("alarmId") Long alarmId);

    @Delete("DELETE FROM biz_ai_screenshot WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    /** 幂等判断：同告警同地址同类型已存在时返回主键，不存在返回 null。 */
    @Select("SELECT id FROM biz_ai_screenshot WHERE alarm_id=#{alarmId} AND screenshot_url=#{url} AND screenshot_type=#{type} LIMIT 1")
    Long selectIdByAlarmAndUrl(@Param("alarmId") Long alarmId, @Param("url") String url, @Param("type") Integer type);

    @Insert("INSERT INTO biz_ai_screenshot (alarm_id, screenshot_url, screenshot_type, file_size, width, height) "
            + "VALUES (#{alarmId}, #{screenshotUrl}, #{screenshotType}, #{fileSize}, #{width}, #{height})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiScreenshot screenshot);
}
