-- Database design V1.4: distinguish the source table referenced by parent_id.
-- 0 = no parent, 1 = asset_device, 2 = biz_env_monitor.
ALTER TABLE asset_device_sensor
    ADD COLUMN parent_type TINYINT NULL DEFAULT 0
    COMMENT '父节点类型：0-无父级关联，1-设备，2-监测点'
    AFTER parent_id;

UPDATE asset_device_sensor s
LEFT JOIN asset_device d ON d.id = s.parent_id AND d.is_deleted = 0
LEFT JOIN biz_env_monitor p ON p.id = s.parent_id
SET s.parent_type = CASE
    WHEN p.id IS NOT NULL AND s.monitor_type IN ('PM25', 'PM10', 'NOISE', 'TEMP', 'HUMIDITY', 'WIND') THEN 2
    WHEN d.id IS NOT NULL THEN 1
    WHEN p.id IS NOT NULL THEN 2
    ELSE 0
END
WHERE s.parent_type IS NULL OR s.parent_type NOT IN (0, 1, 2);

CREATE INDEX idx_parent_type_parent_id
    ON asset_device_sensor (parent_type, parent_id);

