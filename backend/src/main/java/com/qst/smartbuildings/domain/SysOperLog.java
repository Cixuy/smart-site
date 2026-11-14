package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志表 (sys_oper_log)
 * 用于安全审计，记录关键操作行为
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作模块（如：设备台账） */
    private String title;
    /** 操作类型（新增/修改/删除） */
    private String operType;
    /** 操作人姓名 */
    private String operByName;
    /** 操作人ID */
    private Long operById;
    /** 请求参数（JSON格式，含修改前后值） */
    private String operParam;
    /** 操作状态：0-成功，1-失败 */
    private Integer status;
    /** 错误信息 */
    private String errorMsg;
    /** 操作时间 */
    private LocalDateTime operTime;
}