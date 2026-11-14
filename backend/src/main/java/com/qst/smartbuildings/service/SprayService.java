package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qst.smartbuildings.dto.SprayConflictCheckRequest;
import com.qst.smartbuildings.dto.SprayOperateRequest;
import com.qst.smartbuildings.dto.SprayOperationLogPageRequest;
import com.qst.smartbuildings.dto.SpraySchedulePageRequest;
import com.qst.smartbuildings.dto.SprayScheduleRequest;
import com.qst.smartbuildings.dto.SprayScheduleStatusRequest;
import com.qst.smartbuildings.dto.SprayStatisticsRequest;
import com.qst.smartbuildings.dto.SprayTriggerRequest;
import com.qst.smartbuildings.vo.SprayActiveTaskVO;
import com.qst.smartbuildings.vo.SprayOperateResultVO;
import com.qst.smartbuildings.vo.SprayOperationLogVO;
import com.qst.smartbuildings.vo.SprayScheduleVO;
import com.qst.smartbuildings.vo.SprayStatisticsVO;
import com.qst.smartbuildings.vo.SprayStatusVO;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 喷淋降尘业务服务
 */
public interface SprayService {

    /** 手动启动 */
    SprayOperateResultVO start(SprayOperateRequest req);

    /** 手动停止 */
    SprayOperateResultVO stop(SprayOperateRequest req);

    /** 查询区域喷淋运行状态 */
    SprayStatusVO status(Long locationId);

    /** 批量查询喷淋运行状态（大屏） */
    List<SprayStatusVO> statusBatch(Long parentLocationId);

    /** 自动联动触发（内部接口） */
    SprayOperateResultVO trigger(SprayTriggerRequest req);

    /** 新增定时任务 */
    SprayScheduleVO createSchedule(SprayScheduleRequest req);

    /** 修改定时任务 */
    SprayScheduleVO updateSchedule(SprayScheduleRequest req);

    /** 定时任务详情 */
    SprayScheduleVO scheduleDetail(Long id);

    /** 定时任务分页 */
    IPage<SprayScheduleVO> schedulePage(SpraySchedulePageRequest req);

    /** 批量删除定时任务（逻辑删除） */
    void deleteSchedule(List<Long> ids);

    /** 启用/禁用定时任务 */
    void scheduleStatus(Long id, Integer status);

    /** 时间冲突校验 */
    boolean conflictCheck(SprayConflictCheckRequest req);

    /** 查询所有启用中的定时任务（含下次执行时间） */
    List<SprayActiveTaskVO> activeTasks();

    /** 操作日志分页 */
    IPage<SprayOperationLogVO> logPage(SprayOperationLogPageRequest req);

    /** 操作日志详情 */
    SprayOperationLogVO logDetail(Long id);

    /** 导出操作日志为 CSV */
    void exportLog(SprayOperationLogPageRequest req, HttpServletResponse response);

    /** 喷淋运行统计 */
    SprayStatisticsVO statistics(SprayStatisticsRequest req);
}
