package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 3D场景配置表 (scene_3d_config)
 * <p>存储3D项目场景的基本信息及模型文件路径。一个项目对应一个3D场景。</p>
 */
@Data
@TableName("scene_3d_config")
public class Scene3dConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID（→ project.id） */
    private Long projectId;

    /** 场景名称 */
    private String sceneName;

    /** 场景中心X坐标 */
    private BigDecimal centerX;

    /** 场景中心Y坐标 */
    private BigDecimal centerY;

    /** 场景中心Z坐标 */
    private BigDecimal centerZ;

    /** 简易几何体模式：1-是，0-否 */
    private Integer isSimpleMode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
