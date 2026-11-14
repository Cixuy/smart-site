package com.qst.smartbuildings.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.qst.smartbuildings.domain.EnvMonitorData;
import com.qst.smartbuildings.domain.ScreenConfig;
import com.qst.smartbuildings.domain.ScreenVideoRotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenSchemaMappingTest {
    @Test
    void screenEntitiesUseDatabaseDesignTableNames() {
        assertEquals("screen_config", tableNameOf(ScreenConfig.class));
        assertEquals("screen_video_rotation", tableNameOf(ScreenVideoRotation.class));
    }

    @Test
    void environmentDataUsesDatabaseDesignParentColumn() throws NoSuchFieldException {
        TableField mapping = EnvMonitorData.class.getDeclaredField("monitorPointId").getAnnotation(TableField.class);
        assertEquals("parent_id", mapping.value());
    }

    private String tableNameOf(Class<?> entityType) {
        return entityType.getAnnotation(TableName.class).value();
    }
}
