package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qst.smartbuildings.domain.SprayOperationLog;
import com.qst.smartbuildings.vo.SprayOperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 喷淋操作日志 Mapper
 */
@Mapper
public interface SprayOperationLogMapper extends BaseMapper<SprayOperationLog> {

    /**
     * 查询某位置最近一条操作日志（用于运行状态推算）
     */
    @Select("SELECT * FROM spray_operation_log WHERE location_id = #{locationId} ORDER BY create_time DESC LIMIT 1")
    SprayOperationLog selectLatestByLocation(@Param("locationId") Long locationId);

    /**
     * 分页查询喷淋操作日志（关联位置名称）
     */
    @Select("<script>" +
            "SELECT l.id, l.location_id AS locationId, loc.location_name AS locationName, " +
            "       l.sensor_id AS sensorId, l.trigger_mode AS triggerMode, l.operation_type AS operationType, " +
            "       l.reason, l.operator_id AS operatorId, l.operator_name AS operatorName, l.create_time AS createTime " +
            "FROM spray_operation_log l " +
            "LEFT JOIN asset_device_location loc ON loc.id = l.location_id AND loc.is_deleted = 0 " +
            "WHERE 1 = 1 " +
            "<if test='locationId != null'> AND l.location_id = #{locationId} </if>" +
            "<if test='triggerMode != null and triggerMode != \"\"'> AND l.trigger_mode = #{triggerMode} </if>" +
            "<if test='operationType != null and operationType != \"\"'> AND l.operation_type = #{operationType} </if>" +
            "<if test='operatorName != null and operatorName != \"\"'> AND l.operator_name LIKE CONCAT('%', #{operatorName}, '%') </if>" +
            "<if test='beginTime != null and beginTime != \"\"'> AND l.create_time &gt;= #{beginTime} </if>" +
            "<if test='endTime != null and endTime != \"\"'> AND l.create_time &lt;= #{endTime} </if>" +
            "ORDER BY l.create_time DESC" +
            "</script>")
    IPage<SprayOperationLogVO> selectPageVo(IPage<SprayOperationLogVO> page,
                                            @Param("locationId") Long locationId,
                                            @Param("triggerMode") String triggerMode,
                                            @Param("operationType") String operationType,
                                            @Param("operatorName") String operatorName,
                                            @Param("beginTime") String beginTime,
                                            @Param("endTime") String endTime);

    /**
     * 查询时间范围内的全部日志（用于统计聚合，按位置+时间排序）
     */
    @Select("<script>" +
            "SELECT * FROM spray_operation_log " +
            "WHERE create_time &gt;= #{beginTime} AND create_time &lt;= #{endTime} " +
            "<if test='locationId != null'> AND location_id = #{locationId} </if>" +
            "ORDER BY location_id ASC, create_time ASC" +
            "</script>")
    List<SprayOperationLog> selectInRange(@Param("locationId") Long locationId,
                                          @Param("beginTime") String beginTime,
                                          @Param("endTime") String endTime);
}
