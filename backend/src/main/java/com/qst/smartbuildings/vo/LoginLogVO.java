package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志 VO
 */
@Data
public class LoginLogVO {
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