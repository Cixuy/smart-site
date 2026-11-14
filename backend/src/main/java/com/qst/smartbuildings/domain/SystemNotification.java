package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知（system_notification）
 *
 * <p>存储系统通知消息，对应首页右上角通知中心。target_user_id = 0 表示全体通知；告警类通知（type=1）在告警产生时自动创建。</p>
 */
@Data
@TableName("system_notification")
public class SystemNotification {

    /** 通知ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 类型：1-告警，2-系统公告，3-任务分配，4-审批 */
    private Integer type;

    /** 接收人用户ID（0表示全体） */
    private Long targetUserId;

    /** 是否已读：1-是，0-否 */
    private Boolean isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;

    /** 点击跳转链接 */
    private String sourceUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
