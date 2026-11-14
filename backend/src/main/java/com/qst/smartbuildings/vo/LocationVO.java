package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 位置详情/列表 VO
 */
@Data
public class LocationVO {
    private Long id;
    private String locationName;
    private Long parentId;
    private Integer level;
    private BigDecimal gisLon;
    private BigDecimal gisLat;
    private Long responsibleUserId;
    /** 负责人姓名 */
    private String responsibleUserName;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
}
