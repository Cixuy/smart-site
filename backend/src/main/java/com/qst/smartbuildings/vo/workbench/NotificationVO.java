package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知消息
 */
@Data
public class NotificationVO {

    /** 通知ID */
    private Long id;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：1-告警，2-系统公告，3-任务分配，4-审批 */
    private Integer type;

    /** 接收人用户ID（0表示全体） */
    private Long targetUserId;

    /** 是否已读 */
    private Boolean isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;

    /** 点击跳转链接 */
    private String sourceUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
