package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 位置树 VO
 */
@Data
public class LocationTreeVO {
    private Long id;
    private String locationName;
    private Long parentId;
    private Integer level;
    private BigDecimal gisLon;
    private BigDecimal gisLat;
    private Integer sort;
    private List<LocationTreeVO> children = new ArrayList<>();
}
