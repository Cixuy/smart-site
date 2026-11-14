package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备位置表 (asset_device_location)
 * 树形结构，定义工地区域层级（如：XX项目 -> 一期 -> 1号楼 -> 东侧作业面）
 */
@Data
@TableName("asset_device_location")
public class AssetDeviceLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 位置名称 */
    private String locationName;
    /** 父节点ID（0为根） */
    private Long parentId;
    /** 层级 */
    private Integer level;
    /** GIS经度（用于3D定位） */
    private BigDecimal gisLon;
    /** GIS纬度 */
    private BigDecimal gisLat;
    /** 负责人ID */
    private Long responsibleUserId;
    /** 排序 */
    private Integer sort;
    /** 状态：1-启用，0-禁用 */
    private Integer status;
    /** 逻辑删除 */
    @TableLogic
    private Integer isDeleted;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
