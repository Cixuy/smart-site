-- ============================================================
-- 3D可视化场景模块 建表语句
-- 引擎：InnoDB / 字符集：utf8mb4
-- 对应文档：数据库表设计与API接口文档.txt（第二部分）
-- ============================================================

-- 2.1 3D场景配置表
CREATE TABLE IF NOT EXISTS `scene_3d_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
    `scene_name` VARCHAR(64) NOT NULL COMMENT '场景名称',
    `model_file_url` VARCHAR(512) DEFAULT NULL COMMENT '模型文件路径',
    `model_file_md5` CHAR(32) DEFAULT NULL COMMENT '文件MD5校验码',
    `center_x` DECIMAL(10, 4) DEFAULT 0 COMMENT '场景中心X坐标',
    `center_y` DECIMAL(10, 4) DEFAULT 0 COMMENT '场景中心Y坐标',
    `center_z` DECIMAL(10, 4) DEFAULT 0 COMMENT '场景中心Z坐标',
    `is_simple_mode` TINYINT(1) DEFAULT 0 COMMENT '简易几何体模式：1-是，0-否',
    `version` VARCHAR(16) DEFAULT NULL COMMENT '模型版本号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='3D场景配置表';

-- 2.2 设备坐标映射表
CREATE TABLE IF NOT EXISTS `device_coordinate_mapping` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `scene_id` BIGINT NOT NULL COMMENT '关联3D场景ID',
    `device_id` BIGINT NOT NULL COMMENT '设备ID（关联设备台账）',
    `pos_x` DECIMAL(10, 4) NOT NULL COMMENT '场景X坐标',
    `pos_y` DECIMAL(10, 4) NOT NULL COMMENT '场景Y坐标（高度）',
    `pos_z` DECIMAL(10, 4) NOT NULL COMMENT '场景Z坐标',
    `rotation_x` DECIMAL(5, 2) DEFAULT 0 COMMENT '旋转角X（度）',
    `rotation_y` DECIMAL(5, 2) DEFAULT 0 COMMENT '旋转角Y（度）',
    `rotation_z` DECIMAL(5, 2) DEFAULT 0 COMMENT '旋转角Z（度）',
    `scale_x` DECIMAL(5, 2) DEFAULT 1.00 COMMENT '缩放比例X',
    `scale_y` DECIMAL(5, 2) DEFAULT 1.00 COMMENT '缩放比例Y',
    `scale_z` DECIMAL(5, 2) DEFAULT 1.00 COMMENT '缩放比例Z',
    `icon_type` TINYINT NOT NULL COMMENT '图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`),
    KEY `idx_scene_id` (`scene_id`),
    KEY `idx_icon_type` (`icon_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备3D坐标映射表';

-- 2.3 3D视频叠加配置表
CREATE TABLE IF NOT EXISTS `scene_video_overlay` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `coordinate_id` BIGINT NOT NULL COMMENT '关联设备坐标ID（必须是摄像头类型）',
    `video_width` INT DEFAULT 320 COMMENT '视频窗口宽度（像素）',
    `video_height` INT DEFAULT 240 COMMENT '视频窗口高度（像素）',
    `offset_x` DECIMAL(5, 2) DEFAULT 0 COMMENT '相对设备坐标的X偏移',
    `offset_y` DECIMAL(5, 2) DEFAULT 20 COMMENT '相对设备坐标的Y偏移',
    `is_transparent` TINYINT(1) DEFAULT 1 COMMENT '是否半透明：1-是，0-否',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coordinate_id` (`coordinate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='3D视频叠加配置表';
