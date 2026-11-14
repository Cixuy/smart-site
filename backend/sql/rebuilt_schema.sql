-- 由《数据库表设计V1.1》生成
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(20) NOT NULL COMMENT '登录账号（字母+数字组合）',
  `password` VARCHAR(100) NOT NULL COMMENT '加密密码（BCrypt/RSA加密）',
  `real_name` VARCHAR(20) NOT NULL COMMENT '真实姓名',
  `phone` VARCHAR(11) NOT NULL COMMENT '手机号',
  `email` VARCHAR(50) COMMENT '邮箱',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用，2-锁定',
  `last_login_time` DATETIME COMMENT '最后登录时间',
  `pwd_expire_time` DATETIME COMMENT '密码过期时间（90天强制改密）',
  `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数（用于5次锁定）',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删，1-删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` VARCHAR(30) NOT NULL COMMENT '角色编码（如：ROLE_ADMIN）',
  `role_name` VARCHAR(30) NOT NULL COMMENT '角色名称（如：系统管理员）',
  `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据权限：1-全部，2-自定义，3-仅本人',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '显示排序',
  `remark` VARCHAR(200) COMMENT '备注',
  `is_system` TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置（1-是，0-否，内置不可删）',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID（0表示根节点）',
  `menu_name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
  `menu_type` TINYINT NOT NULL COMMENT '类型：1-目录，2-菜单，3-按钮',
  `permission_key` VARCHAR(100) COMMENT '权限标识（如：device:add）',
  `path` VARCHAR(200) COMMENT '路由地址',
  `component` VARCHAR(255) COMMENT '组件路径',
  `icon` VARCHAR(100) COMMENT '菜单图标',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `is_visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示（1-显示，0-隐藏）',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `title` VARCHAR(50) NOT NULL COMMENT '操作模块（如：设备台账）',
  `oper_type` VARCHAR(20) NOT NULL COMMENT '操作类型（新增/修改/删除）',
  `oper_by_name` VARCHAR(20) NOT NULL COMMENT '操作人姓名',
  `oper_by_id` BIGINT NOT NULL COMMENT '操作人ID',
  `oper_param` TEXT COMMENT '请求参数（JSON格式，含修改前后值）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '操作状态：0-成功，1-失败',
  `error_msg` TEXT COMMENT '错误信息',
  `oper_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `username` VARCHAR(20) NOT NULL COMMENT '登录账号',
  `login_ip` VARCHAR(50) NOT NULL COMMENT '登录IP',
  `browser` VARCHAR(50) COMMENT '浏览器',
  `os` VARCHAR(50) COMMENT '操作系统',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-成功，1-失败，2-锁定',
  `msg` VARCHAR(255) COMMENT '提示消息',
  `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_device_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '类型ID',
  `type_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `type_code` VARCHAR(50) NOT NULL COMMENT '分类编码（如：LH-JZ-TD）',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父节点ID（0为根）',
  `level` INT NOT NULL DEFAULT 1 COMMENT '层级（1-4级）',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `remark` VARCHAR(200) COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_device_location` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '位置ID',
  `location_name` VARCHAR(100) NOT NULL COMMENT '位置名称',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父节点ID（0为根）',
  `level` INT NOT NULL DEFAULT 1 COMMENT '层级',
  `gis_lon` DECIMAL(10,7) COMMENT 'GIS经度（用于3D定位）',
  `gis_lat` DECIMAL(10,7) COMMENT 'GIS纬度',
  `responsible_user_id` BIGINT COMMENT '负责人ID',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_device` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `device_code` VARCHAR(30) NOT NULL COMMENT '设备编码（系统生成，唯一）',
  `device_name` VARCHAR(50) NOT NULL COMMENT '设备名称',
  `type_id` BIGINT NOT NULL COMMENT '设备类型ID',
  `location_id` BIGINT NOT NULL COMMENT '安装位置ID',
  `brand` VARCHAR(50) NOT NULL COMMENT '品牌',
  `model` VARCHAR(50) NOT NULL COMMENT '型号',
  `production_date` DATE NOT NULL COMMENT '生产日期',
  `supply_date` DATE COMMENT '供货日期',
  `acceptance_date` DATE COMMENT '验收日期',
  `install_date` DATE COMMENT '安装日期',
  `enable_date` DATE COMMENT '启用日期',
  `expected_scrap_date` DATE COMMENT '预计报废日期',
  `actual_scrap_date` DATE COMMENT '实际报废日期',
  `online_status` TINYINT NOT NULL DEFAULT 0 COMMENT '在线状态：0-离线，1-在线',
  `enable_status` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0-禁用，1-启用',
  `qr_code_url` VARCHAR(255) COMMENT '二维码图片地址',
  `certificate_url` VARCHAR(255) COMMENT '合格证附件地址',
  `last_online_time` DATETIME COMMENT '最后在线时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_code` (`device_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_device_sensor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '监测点ID',
  `sensor_name` VARCHAR(50) NOT NULL COMMENT '监测点名称（如：塔吊1-风速）',
  `device_id` BIGINT NOT NULL COMMENT '所属设备ID',
  `monitor_type` VARCHAR(30) NOT NULL COMMENT '监测类型（wind_speed, load, pm25等）',
  `unit` VARCHAR(10) NOT NULL COMMENT '单位（m/s, kN等）',
  `warning_upper` DECIMAL(10,2) COMMENT '预警上限',
  `warning_lower` DECIMAL(10,2) COMMENT '预警下限',
  `alarm_upper` DECIMAL(10,2) NOT NULL COMMENT '报警上限',
  `alarm_lower` DECIMAL(10,2) COMMENT '报警下限',
  `spray_threshold` DECIMAL(10,2) COMMENT '喷淋联动阈值（仅环境类）',
  `refresh_rate` INT NOT NULL DEFAULT 5 COMMENT '刷新频率（秒）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-运行，0-停止，2-故障',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_device_offline_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `device_id` BIGINT NOT NULL COMMENT '设备ID',
  `offline_time` DATETIME NOT NULL COMMENT '离线时间',
  `online_time` DATETIME COMMENT '上线时间（为空表示至今未恢复）',
  `duration_seconds` BIGINT COMMENT '持续离线时长（秒）',
  `reason` VARCHAR(20) NOT NULL COMMENT '触发原因（网络异常/设备故障/主动断开）',
  `handler` VARCHAR(20) COMMENT '处理人（主动断开时记录）',
  `remark` VARCHAR(200) COMMENT '处理备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `spray_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作ID',
  `location_id` BIGINT NOT NULL COMMENT '关联位置ID',
  `sensor_id` BIGINT COMMENT '关联监测点ID（触发联动的环境传感器）',
  `trigger_mode` VARCHAR(20) NOT NULL COMMENT '触发方式：auto-自动联动，manual-手动，schedule-定时',
  `operation_type` VARCHAR(10) NOT NULL COMMENT '操作类型：start-启动，stop-关闭',
  `reason` VARCHAR(200) COMMENT '操作原因（手动操作时必填）',
  `operator_id` BIGINT COMMENT '操作人ID（自动时为0或NULL）',
  `operator_name` VARCHAR(20) COMMENT '操作人姓名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `spray_schedule_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_name` VARCHAR(50) NOT NULL COMMENT '任务名称',
  `location_id` BIGINT NOT NULL COMMENT '执行位置ID',
  `start_time` TIME NOT NULL COMMENT '开始时间（如：08:00:00）',
  `duration_minutes` INT NOT NULL COMMENT '持续时长（分钟）',
  `cycle_type` VARCHAR(20) NOT NULL COMMENT '执行周期：daily-每天，weekly-每周，monthly-每月',
  `cycle_config` VARCHAR(100) COMMENT '周期配置（如每周的周一、周三，JSON格式存储）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `remark` VARCHAR(200) COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `dashboard_user_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
  `card_id` VARCHAR(32) NOT NULL COMMENT '卡片组件唯一标识',
  `card_title` VARCHAR(64) COMMENT '卡片自定义标题',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  `is_visible` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可见：1-是，0-否',
  `config_json` JSON COMMENT '卡片个性化配置',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `system_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `title` VARCHAR(128) NOT NULL COMMENT '通知标题',
  `content` TEXT NOT NULL COMMENT '通知内容',
  `type` TINYINT NOT NULL COMMENT '类型：1-告警，2-系统公告，3-任务分配，4-审批',
  `target_user_id` BIGINT NOT NULL COMMENT '接收人用户ID（0表示全体）',
  `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：1-是，0-否',
  `read_time` DATETIME COMMENT '阅读时间',
  `source_url` VARCHAR(256) COMMENT '点击跳转链接',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `search_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `keyword` VARCHAR(64) NOT NULL COMMENT '搜索关键词',
  `search_type` TINYINT COMMENT '搜索类型：1-设备，2-告警，3-用户',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `screen_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `screen_name` VARCHAR(64) NOT NULL COMMENT '大屏名称',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `layout_config` JSON NOT NULL COMMENT '布局配置',
  `refresh_interval` INT NOT NULL DEFAULT 5 COMMENT '全局刷新间隔（秒）',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认大屏：1-是，0-否',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `screen_video_rotation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id` BIGINT NOT NULL COMMENT '关联大屏ID',
  `camera_id` BIGINT NOT NULL COMMENT '摄像头ID（关联设备台账）',
  `display_order` INT NOT NULL DEFAULT 0 COMMENT '轮播顺序',
  `duration` INT NOT NULL DEFAULT 10 COMMENT '单路显示时长（秒）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `scene_3d_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `scene_name` VARCHAR(64) NOT NULL COMMENT '场景名称',
  `model_file_url` VARCHAR(512) COMMENT '模型文件路径',
  `model_file_md5` CHAR(32) COMMENT '文件MD5校验码',
  `center_x` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '场景中心X坐标',
  `center_y` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '场景中心Y坐标',
  `center_z` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '场景中心Z坐标',
  `is_simple_mode` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '简易几何体模式：1-是，0-否',
  `version` VARCHAR(16) COMMENT '模型版本号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `device_coordinate_mapping` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene_id` BIGINT NOT NULL COMMENT '关联3D场景ID',
  `device_id` BIGINT NOT NULL COMMENT '设备ID（关联设备台账）',
  `pos_x` DECIMAL(10,4) NOT NULL COMMENT '场景X坐标',
  `pos_y` DECIMAL(10,4) NOT NULL COMMENT '场景Y坐标（高度）',
  `pos_z` DECIMAL(10,4) NOT NULL COMMENT '场景Z坐标',
  `rotation_x` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '旋转角X（度）',
  `rotation_y` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '旋转角Y（度）',
  `rotation_z` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '旋转角Z（度）',
  `scale_x` DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '缩放比例X',
  `scale_y` DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '缩放比例Y',
  `scale_z` DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '缩放比例Z',
  `icon_type` TINYINT NOT NULL COMMENT '图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `scene_video_overlay` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `coordinate_id` BIGINT NOT NULL COMMENT '关联设备坐标ID（必须是摄像头类型）',
  `video_width` INT NOT NULL DEFAULT 320 COMMENT '视频窗口宽度（像素）',
  `video_height` INT NOT NULL DEFAULT 240 COMMENT '视频窗口高度（像素）',
  `offset_x` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '相对设备坐标的X偏移',
  `offset_y` DECIMAL(5,2) NOT NULL DEFAULT 20 COMMENT '相对设备坐标的Y偏移',
  `is_transparent` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否半透明：1-是，0-否',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coordinate_id` (`coordinate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_tower_crane_realtime` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `collect_time` DATETIME(3) NOT NULL COMMENT '采集时间（毫秒精度）',
  `load_weight` DECIMAL(10,2) COMMENT '当前吊重（kN）',
  `amplitude` DECIMAL(6,1) COMMENT '幅度（m）',
  `moment` DECIMAL(10,2) COMMENT '力矩（kN·m）',
  `moment_percent` DECIMAL(5,2) COMMENT '力矩百分比（%）',
  `wind_speed` DECIMAL(4,1) COMMENT '风速（m/s）',
  `height` DECIMAL(6,1) COMMENT '吊钩高度（m），允许负值',
  `slew_angle` DECIMAL(5,1) COMMENT '回转角度（°）',
  `alarm_status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-预警，2-报警',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_tower_crane_work_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `start_time` DATETIME NOT NULL COMMENT '作业开始时间',
  `end_time` DATETIME NOT NULL COMMENT '作业结束时间',
  `avg_load` DECIMAL(10,2) COMMENT '平均吊重（kN）',
  `max_load` DECIMAL(10,2) COMMENT '最大吊重（kN）',
  `max_load_ratio` DECIMAL(5,2) COMMENT '最大吊重占比（%）',
  `max_wind_speed` DECIMAL(4,1) COMMENT '最大风速（m/s）',
  `max_height` DECIMAL(6,1) COMMENT '最大高度（m）',
  `hook_radius` DECIMAL(6,1) COMMENT '吊点半径（m）',
  `unload_position` VARCHAR(200) COMMENT '卸料位置描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_tower_crane_threshold` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `metric_type` VARCHAR(20) NOT NULL COMMENT '指标类型：load-吊重，moment-力矩，moment_percent-力矩百分比，wind_speed-风速，am',
  `unit` VARCHAR(10) NOT NULL COMMENT '单位（kN、kN·m、%、m/s、m）',
  `compare_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '比较方式：1-数值直接比较，2-按额定值百分比比较',
  `rated_value` DECIMAL(10,2) COMMENT '额定基准值（compare_mode=2 时必填，如额定载荷/额定力矩）',
  `warn_upper` DECIMAL(10,2) COMMENT '预警上限（超过触发预警）',
  `warn_lower` DECIMAL(10,2) COMMENT '预警下限（低于触发预警）',
  `alarm_upper` DECIMAL(10,2) COMMENT '报警上限（超过触发报警）',
  `alarm_lower` DECIMAL(10,2) COMMENT '报警下限（低于触发报警）',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
  `remark` VARCHAR(200) COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_hoist_realtime` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `collect_time` DATETIME(3) NOT NULL COMMENT '采集时间（毫秒精度）',
  `load_weight` DECIMAL(10,2) COMMENT '当前载重（kN）',
  `load_percent` DECIMAL(5,2) COMMENT '载重百分比（%）',
  `height` DECIMAL(6,1) COMMENT '当前高度（m）',
  `run_direction` TINYINT NOT NULL DEFAULT 0 COMMENT '运行方向：0-停止，1-上升，2-下降',
  `front_door_status` TINYINT NOT NULL DEFAULT 0 COMMENT '前门状态：0-关，1-开',
  `rear_door_status` TINYINT NOT NULL DEFAULT 0 COMMENT '后门状态：0-关，1-开',
  `passenger_count` INT COMMENT '当前载人数',
  `wind_speed` DECIMAL(4,1) COMMENT '风速（m/s）',
  `interlock_alarm` TINYINT NOT NULL DEFAULT 0 COMMENT '联锁告警：0-正常，1-双门同开，2-超载，3-超速，4-超高',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_hoist_work_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `start_time` DATETIME NOT NULL COMMENT '作业开始时间',
  `end_time` DATETIME NOT NULL COMMENT '作业结束时间',
  `avg_load` DECIMAL(10,2) COMMENT '平均载重（kN）',
  `max_load` DECIMAL(10,2) COMMENT '最大载重（kN）',
  `max_passengers` INT COMMENT '最大载人数',
  `floor_range` VARCHAR(50) COMMENT '起止楼层（如 "1F-15F"）',
  `avg_wind_speed` DECIMAL(4,1) COMMENT '平均风速（m/s）',
  `max_tilt_angle` DECIMAL(5,2) COMMENT '最大倾斜角（°）',
  `height_range` DECIMAL(6,1) COMMENT '高度变化范围（m）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_hoist_threshold` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `metric_type` VARCHAR(20) NOT NULL COMMENT '指标类型：load_percent-载重百分比，passenger_count-载人数，wind_speed-风速，sp',
  `unit` VARCHAR(10) NOT NULL COMMENT '单位（%、人、m/s、m/min、m）',
  `compare_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '比较方式：1-数值直接比较，2-按额定值百分比比较，3-布尔条件（离散事件，如双门同开）',
  `rated_value` DECIMAL(10,2) COMMENT '额定基准值（compare_mode=2 时必填，如额定载重/额定人数）',
  `warn_upper` DECIMAL(10,2) COMMENT '预警上限',
  `warn_lower` DECIMAL(10,2) COMMENT '预警下限',
  `alarm_upper` DECIMAL(10,2) COMMENT '报警上限（compare_mode=3 时恒触发报警级）',
  `alarm_lower` DECIMAL(10,2) COMMENT '报警下限',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
  `remark` VARCHAR(200) COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_camera` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `camera_name` VARCHAR(100) NOT NULL COMMENT '摄像头名称',
  `device_id` VARCHAR(30) COMMENT '关联设备台账（可选）',
  `location_id` BIGINT NOT NULL COMMENT '关联位置树ID',
  `stream_url` VARCHAR(500) NOT NULL COMMENT 'RTSP/RTMP视频流地址',
  `protocol_type` VARCHAR(20) NOT NULL COMMENT '流媒体协议类型',
  `online_status` TINYINT NOT NULL DEFAULT 0 COMMENT '在线状态：0-离线，1-在线',
  `ai_capabilities` JSON COMMENT 'AI能力标签数组',
  `last_online_time` DATETIME COMMENT '最后在线时间',
  `remark` VARCHAR(500) COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_video_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `camera_id` BIGINT NOT NULL COMMENT '关联摄像头ID',
  `record_type` TINYINT NOT NULL COMMENT '类型：1-录像，2-截图，3-AI告警截图',
  `file_path` VARCHAR(500) NOT NULL COMMENT '文件存储路径/OSS Key',
  `start_time` DATETIME NOT NULL COMMENT '录制/截图开始时间',
  `end_time` DATETIME COMMENT '录制结束时间（截图可为NULL）',
  `file_size` BIGINT COMMENT '文件大小（Bytes）',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间（≥30天）',
  `trigger_source` VARCHAR(50) COMMENT '触发来源',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_env_monitor_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `monitor_point_id` BIGINT NOT NULL COMMENT '关联监测点ID',
  `device_id` VARCHAR(30) NOT NULL COMMENT '关联设备编码',
  `collect_time` DATETIME(3) NOT NULL COMMENT '采集时间（毫秒精度）',
  `metric_type` VARCHAR(20) NOT NULL COMMENT '指标类型',
  `metric_value` DECIMAL(10,2) NOT NULL COMMENT '监测数值',
  `unit` VARCHAR(10) NOT NULL COMMENT '单位（μg/m³, dB, ℃, m/s）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-预警，2-报警',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_env_daily_stat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `monitor_point_id` BIGINT NOT NULL COMMENT '关联监测点ID',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `metric_type` VARCHAR(20) NOT NULL COMMENT '指标类型',
  `max_value` DECIMAL(10,2) NOT NULL COMMENT '当日最大值',
  `min_value` DECIMAL(10,2) NOT NULL COMMENT '当日最小值',
  `avg_value` DECIMAL(10,2) NOT NULL COMMENT '当日平均值',
  `alarm_count` INT NOT NULL DEFAULT 0 COMMENT '当日报警次数',
  `warn_count` INT NOT NULL DEFAULT 0 COMMENT '当日预警次数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_env_threshold` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
  `monitor_point_id` BIGINT NOT NULL COMMENT '关联监测点ID',
  `metric_type` VARCHAR(20) NOT NULL COMMENT '指标类型',
  `unit` VARCHAR(10) NOT NULL COMMENT '单位（μg/m³、dB、℃、%、m/s）',
  `warn_upper` DECIMAL(10,2) COMMENT '预警上限（超过触发预警）',
  `warn_lower` DECIMAL(10,2) COMMENT '预警下限（低于触发预警）',
  `alarm_upper` DECIMAL(10,2) COMMENT '报警上限（超过触发报警）',
  `alarm_lower` DECIMAL(10,2) COMMENT '报警下限（低于触发报警）',
  `spray_threshold` DECIMAL(10,2) COMMENT '喷淋联动阈值（如 PM2.5≥150 μg/m³ 启动喷淋）',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
  `remark` VARCHAR(200) COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_ai_screenshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_id` BIGINT NOT NULL COMMENT '关联告警ID',
  `screenshot_url` VARCHAR(500) NOT NULL COMMENT '截图存储路径/OSS Key',
  `screenshot_type` TINYINT NOT NULL DEFAULT 1 COMMENT '截图类型：1-原始截图，2-标注截图',
  `file_size` BIGINT COMMENT '文件大小（Bytes）',
  `width` INT COMMENT '图片宽度（像素）',
  `height` INT COMMENT '图片高度（像素）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_ai_inference_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `camera_id` BIGINT NOT NULL COMMENT '关联摄像头ID',
  `inference_time` DATETIME(3) NOT NULL COMMENT '推理时间（毫秒精度）',
  `detection_type` VARCHAR(20) NOT NULL COMMENT '检测类型：helmet, vest, smoking, fire',
  `confidence` DECIMAL(5,4) NOT NULL COMMENT '置信度（0~1）',
  `has_alarm` TINYINT NOT NULL DEFAULT 0 COMMENT '是否触发告警：0-否，1-是',
  `bbox_coords` JSON COMMENT '目标边界框坐标 [{x,y,width,height}]',
  `inference_duration_ms` INT COMMENT '推理耗时（毫秒）',
  `image_url` VARCHAR(500) COMMENT '推理原图存储路径',
  `error_msg` VARCHAR(255) COMMENT '错误信息（推理失败时记录）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_alarm_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_no` VARCHAR(32) NOT NULL COMMENT '告警编号（业务编号，如 AL20260805001）',
  `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
  `source_type` TINYINT NOT NULL COMMENT '告警来源：1-设备监测，2-环境监测，3-AI识别',
  `alarm_level` TINYINT NOT NULL COMMENT '告警级别：1-预警，2-警报，3-控制',
  `alarm_type_code` VARCHAR(30) NOT NULL COMMENT '告警类型编码（如 OVERLOAD、PM25_EXCEED、NO_HELMET）',
  `alarm_content` VARCHAR(500) NOT NULL COMMENT '告警内容描述（如“塔吊力矩超标”）',
  `current_value` DECIMAL(10,2) COMMENT '触发告警时的当前数据值',
  `threshold_range` VARCHAR(100) COMMENT '阈值范围描述（如“≥100% 额定值”）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处置状态：0-未处置，1-处置中，2-已处置',
  `device_id` BIGINT COMMENT '关联设备ID（非AI告警时使用）',
  `monitor_point_id` BIGINT COMMENT '关联监测点ID（设备/环境告警时使用）',
  `camera_id` BIGINT COMMENT '关联摄像头ID（AI告警时使用）',
  `related_alarm_id` BIGINT COMMENT '关联告警ID（同一事件触发的多条告警）',
  `alarm_time` DATETIME(3) NOT NULL COMMENT '告警发生时间（毫秒精度）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alarm_no` (`alarm_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_alarm_disposal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_id` BIGINT NOT NULL COMMENT '关联告警ID',
  `operator_id` BIGINT NOT NULL COMMENT '处置人ID',
  `operator_name` VARCHAR(20) NOT NULL COMMENT '处置人姓名',
  `disposal_content` TEXT NOT NULL COMMENT '处置内容（文本描述）',
  `disposal_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处置时间',
  `disposal_conclusion` TINYINT NOT NULL COMMENT '处置结论：1-属实，2-误报，3-其他',
  `false_reason` VARCHAR(255) COMMENT '误报原因（结论为“误报”时必填）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_alarm_recovery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_id` BIGINT NOT NULL COMMENT '关联告警ID（唯一恢复记录）',
  `recovery_time` DATETIME(3) NOT NULL COMMENT '恢复时间（毫秒精度）',
  `recovery_value` DECIMAL(10,2) COMMENT '恢复时的数据值',
  `recovery_note` VARCHAR(200) COMMENT '恢复备注（如“力矩值回落至80%额定值”）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alarm_id` (`alarm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_alarm_daily_stat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` BIGINT NOT NULL COMMENT '项目ID',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `alarm_level` TINYINT NOT NULL COMMENT '告警级别：1-预警，2-警报，3-控制',
  `source_type` TINYINT NOT NULL COMMENT '告警来源：1-设备监测，2-环境监测，3-AI识别',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '当日该类告警总数',
  `pending_count` INT NOT NULL DEFAULT 0 COMMENT '当日该类未处置告警数',
  `resolved_count` INT NOT NULL DEFAULT 0 COMMENT '当日该类已处置告警数',
  `false_alarm_count` INT NOT NULL DEFAULT 0 COMMENT '当日该类误报告警数',
  `avg_disposal_minutes` DECIMAL(8,2) COMMENT '当日该类平均处置时长（分钟）',
  `max_disposal_minutes` DECIMAL(8,2) COMMENT '当日该类最大处置时长（分钟）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_alarm_push_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_id` BIGINT NOT NULL COMMENT '关联告警ID',
  `push_channel` TINYINT NOT NULL COMMENT '推送渠道：1-短信，2-企业微信，3-钉钉，4-邮件',
  `push_target` VARCHAR(200) NOT NULL COMMENT '推送目标（手机号/企微ID/钉钉ID/邮箱）',
  `push_status` TINYINT NOT NULL DEFAULT 0 COMMENT '推送状态：0-待推送，1-推送成功，2-推送失败',
  `push_time` DATETIME COMMENT '推送时间',
  `response_code` VARCHAR(20) COMMENT '外部接口响应码',
  `response_msg` VARCHAR(500) COMMENT '外部接口响应消息',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_chat_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID（同一会话多轮对话）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `project_id` BIGINT COMMENT '当前提问关联项目ID（数据权限隔离）',
  `question` TEXT NOT NULL COMMENT '用户提问内容',
  `answer` TEXT NOT NULL COMMENT 'AI回答内容',
  `question_time` DATETIME NOT NULL COMMENT '提问时间',
  `answer_time` DATETIME NOT NULL COMMENT '回答时间',
  `response_duration_ms` INT COMMENT 'AI响应耗时（毫秒）',
  `intent_type` VARCHAR(20) COMMENT '意图类型：safety_query, alarm_analysis, device_diagnosis, suggest',
  `context_json` JSON COMMENT '上下文信息（如引用的设备/告警ID等）',
  `token_usage` INT COMMENT '本次对话消耗Token数',
  `is_success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功：1-是，0-否',
  `error_msg` VARCHAR(255) COMMENT '错误信息（调用失败时记录）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `biz_chat_feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chat_record_id` BIGINT NOT NULL COMMENT '关联对话记录ID（每条对话仅可反馈一次）',
  `user_id` BIGINT NOT NULL COMMENT '反馈人ID',
  `rating` TINYINT NOT NULL COMMENT '评分（1~5星）',
  `is_helpful` TINYINT NOT NULL COMMENT '是否有帮助：1-是，0-否',
  `feedback_content` VARCHAR(500) COMMENT '反馈内容（文本描述）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_record_id` (`chat_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_code` VARCHAR(32) NOT NULL COMMENT '项目编码',
  `project_name` VARCHAR(128) NOT NULL COMMENT '项目名称',
  `address` VARCHAR(256) COMMENT '项目地址',
  `longitude` DECIMAL(10,6) COMMENT '经度坐标',
  `latitude` DECIMAL(10,6) COMMENT '纬度坐标',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-进行中，2-已竣工，3-已停工',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
