package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备类型实体，对应表 asset_device_type。
 * 树形结构，定义设备分类体系（如：大型机械->起重设备->塔吊）。
 */
@Data
@TableName("asset_device_type")
public class AssetDeviceType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String typeName;

    /** 分类编码（如：LH-JZ-TD） */
    private String typeCode;

    /** 父节点ID（0为根） */
    private Long parentId;

    /** 层级（1-4级） */
    private Integer level;

    /** 排序 */
    private Integer sort;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 逻辑删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
