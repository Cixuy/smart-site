package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志表 (sys_login_log)
 * 记录登录行为，用于安全分析与审计
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录账号 */
    private String username;
    /** 登录IP */
    private String loginIp;
    /** 浏览器 */
    private String browser;
    /** 操作系统 */
    private String os;
    /** 状态：0-成功，1-失败，2-锁定 */
    private Integer status;
    /** 提示消息 */
    private String msg;
    /** 登录时间 */
    private LocalDateTime loginTime;
}