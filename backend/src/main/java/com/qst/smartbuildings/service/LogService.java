package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.SysLoginLog;
import com.qst.smartbuildings.domain.SysOperLog;
import com.qst.smartbuildings.exception.LogException;
import com.qst.smartbuildings.mapper.SysLoginLogMapper;
import com.qst.smartbuildings.mapper.SysOperLogMapper;
import com.qst.smartbuildings.vo.LoginLogVO;
import com.qst.smartbuildings.vo.OperLogVO;
import com.qst.smartbuildings.vo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 日志管理服务
 */
@Service
public class LogService {

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    public LogService(SysLoginLogMapper loginLogMapper, SysOperLogMapper operLogMapper) {
        this.loginLogMapper = loginLogMapper;
        this.operLogMapper = operLogMapper;
    }

    /**
     * 分页查询登录日志
     */
    public PageResult<LoginLogVO> pageLoginLogs(long pageNum, long pageSize, String username, Integer status,
                                                 String loginIp, String beginTime, String endTime) {
        LocalDateTime begin = parseTime(beginTime);
        LocalDateTime end = parseTime(endTime);
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new LogException("开始时间不能晚于结束时间");
        }

        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .eq(status != null, SysLoginLog::getStatus, status)
                .like(StringUtils.hasText(loginIp), SysLoginLog::getLoginIp, loginIp)
                .ge(begin != null, SysLoginLog::getLoginTime, begin)
                .le(end != null, SysLoginLog::getLoginTime, end)
                .orderByDesc(SysLoginLog::getLoginTime);
        Page<SysLoginLog> page = loginLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<LoginLogVO> records = page.getRecords().stream().map(this::toLoginLogVO).toList();
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 分页查询操作日志
     */
    public PageResult<OperLogVO> pageOperLogs(long pageNum, long pageSize, String title, String operType,
                                              String operByName, Integer status, String beginTime, String endTime) {
        LocalDateTime begin = parseTime(beginTime);
        LocalDateTime end = parseTime(endTime);
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new LogException("开始时间不能晚于结束时间");
        }

        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(title), SysOperLog::getTitle, title)
                .eq(StringUtils.hasText(operType), SysOperLog::getOperType, operType)
                .like(StringUtils.hasText(operByName), SysOperLog::getOperByName, operByName)
                .eq(status != null, SysOperLog::getStatus, status)
                .ge(begin != null, SysOperLog::getOperTime, begin)
                .le(end != null, SysOperLog::getOperTime, end)
                .orderByDesc(SysOperLog::getOperTime);
        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OperLogVO> records = page.getRecords().stream().map(this::toOperLogVO).toList();
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 记录登录日志
     *
     * @param username 登录账号
     * @param loginIp  登录IP
     * @param browser  浏览器
     * @param os       操作系统
     * @param status   状态：0-成功，1-失败，2-锁定
     * @param msg      提示消息
     */
    public void recordLoginLog(String username, String loginIp, String browser, String os, Integer status, String msg) {
        SysLoginLog entity = new SysLoginLog();
        entity.setUsername(username == null ? "" : username);
        entity.setLoginIp(loginIp == null ? "" : loginIp);
        entity.setBrowser(browser);
        entity.setOs(os);
        entity.setStatus(status);
        entity.setMsg(msg);
        entity.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(entity);
    }

    /**
     * 记录操作日志
     *
     * @param title     操作模块
     * @param operType  操作类型（新增/修改/删除）
     * @param operById  操作人ID
     * @param operByName 操作人姓名
     * @param operParam 请求参数（JSON格式）
     * @param status    操作状态：0-成功，1-失败
     * @param errorMsg  错误信息
     */
    public void recordOperLog(String title, String operType, Long operById, String operByName,
                              String operParam, Integer status, String errorMsg) {
        SysOperLog entity = new SysOperLog();
        entity.setTitle(title);
        entity.setOperType(operType);
        entity.setOperById(operById);
        entity.setOperByName(operByName);
        entity.setOperParam(operParam);
        entity.setStatus(status);
        entity.setErrorMsg(errorMsg);
        entity.setOperTime(LocalDateTime.now());
        operLogMapper.insert(entity);
    }

    /**
     * 查询操作日志详情
     */
    public OperLogVO getOperLogDetail(Long id) {
        if (id == null) {
            throw new LogException("日志ID不能为空");
        }
        SysOperLog operLog = operLogMapper.selectById(id);
        if (operLog == null) {
            throw new LogException("操作日志不存在");
        }
        return toOperLogVO(operLog);
    }

    // ==================== 私有方法 ====================

    private LoginLogVO toLoginLogVO(SysLoginLog entity) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setLoginIp(entity.getLoginIp());
        vo.setBrowser(entity.getBrowser());
        vo.setOs(entity.getOs());
        vo.setStatus(entity.getStatus());
        vo.setMsg(entity.getMsg());
        vo.setLoginTime(entity.getLoginTime());
        return vo;
    }

    private OperLogVO toOperLogVO(SysOperLog entity) {
        OperLogVO vo = new OperLogVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setOperType(entity.getOperType());
        vo.setOperByName(entity.getOperByName());
        vo.setOperById(entity.getOperById());
        vo.setOperParam(entity.getOperParam());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setOperTime(entity.getOperTime());
        return vo;
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        throw new LogException("时间参数格式不正确，应为 yyyy-MM-dd HH:mm:ss");
    }
}