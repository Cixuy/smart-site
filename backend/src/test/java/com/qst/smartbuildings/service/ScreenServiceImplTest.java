package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.qst.smartbuildings.domain.ScreenConfig;
import com.qst.smartbuildings.domain.ScreenVideoRotation;
import com.qst.smartbuildings.mapper.ScreenConfigMapper;
import com.qst.smartbuildings.mapper.ScreenDataMapper;
import com.qst.smartbuildings.mapper.ScreenVideoRotationMapper;
import com.qst.smartbuildings.service.impl.ScreenServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenServiceImplTest {
    @Test
    void deleteConfigDeletesVideoRotationsBeforeScreenConfig() {
        ScreenConfigMapper configMapper = mock(ScreenConfigMapper.class);
        ScreenVideoRotationMapper rotationMapper = mock(ScreenVideoRotationMapper.class);
        ScreenDataMapper dataMapper = mock(ScreenDataMapper.class);
        ScreenConfig config = new ScreenConfig();
        config.setId(7L);

        when(configMapper.selectById(7L)).thenReturn(config);
        when(rotationMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(configMapper.deleteById(7L)).thenReturn(1);

        ScreenServiceImpl service = new ScreenServiceImpl(configMapper, rotationMapper, dataMapper);

        assertTrue(service.deleteConfig(7L));
        InOrder order = inOrder(rotationMapper, configMapper);
        order.verify(rotationMapper).delete(any(Wrapper.class));
        order.verify(configMapper).deleteById(7L);
    }
}
