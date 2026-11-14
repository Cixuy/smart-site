package com.qst.smartbuildings.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeviceTypeTreeVO {
    private Long id;
    private String typeName;
    private String typeCode;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private Integer status;
    private String remark;
    private List<DeviceTypeTreeVO> children = new ArrayList<>();
}
