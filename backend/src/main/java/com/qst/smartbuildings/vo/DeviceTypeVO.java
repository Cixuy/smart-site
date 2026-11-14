package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceTypeVO {
    private Long id;
    private String typeName;
    private String typeCode;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
