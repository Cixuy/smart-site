package com.qst.smartbuildings.service;

import com.qst.smartbuildings.domain.DeviceType;
import com.qst.smartbuildings.dto.UpdateDeviceTypeRequest;
import com.qst.smartbuildings.exception.DeviceTypeException;
import com.qst.smartbuildings.mapper.DeviceTypeMapper;
import com.qst.smartbuildings.vo.DeviceTypeTreeVO;
import com.qst.smartbuildings.vo.DeviceTypeVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTypeServiceTest {
    @Mock
    private DeviceTypeMapper deviceTypeMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private DeviceTypeService service;

    @Test
    void listMapsNullTextFieldsToEmptyStrings() {
        DeviceType record = deviceType(1L);
        when(deviceTypeMapper.selectList(any())).thenReturn(List.of(record));

        List<DeviceTypeVO> result = service.list(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTypeName()).isEmpty();
        assertThat(result.get(0).getTypeCode()).isEmpty();
        assertThat(result.get(0).getRemark()).isEmpty();
    }

    @Test
    void treeMapsNullTextFieldsToEmptyStrings() {
        DeviceType record = deviceType(1L);
        when(deviceTypeMapper.selectList(any())).thenReturn(List.of(record));

        List<DeviceTypeTreeVO> result = service.tree(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTypeName()).isEmpty();
        assertThat(result.get(0).getTypeCode()).isEmpty();
        assertThat(result.get(0).getRemark()).isEmpty();
    }

    @Test
    void updatePersistsAllEditableFields() {
        DeviceType existing = deviceType(10L);
        existing.setTypeName("旧名称");
        existing.setTypeCode("OLD");
        existing.setRemark("旧描述");
        when(deviceTypeMapper.selectOne(any())).thenReturn(existing);
        when(deviceTypeMapper.selectCount(any())).thenReturn(0L);
        when(deviceTypeMapper.updateById(any(DeviceType.class))).thenReturn(1);

        UpdateDeviceTypeRequest request = new UpdateDeviceTypeRequest();
        request.setId(10L);
        request.setTypeName("新名称");
        request.setTypeCode("NEW");
        request.setParentId(0L);
        request.setLevel(1);
        request.setRemark("新描述");

        service.update(request);

        ArgumentCaptor<DeviceType> captor = ArgumentCaptor.forClass(DeviceType.class);
        verify(deviceTypeMapper).updateById(captor.capture());
        DeviceType update = captor.getValue();
        assertThat(update.getId()).isEqualTo(10L);
        assertThat(update.getTypeName()).isEqualTo("新名称");
        assertThat(update.getTypeCode()).isEqualTo("NEW");
        assertThat(update.getParentId()).isZero();
        assertThat(update.getLevel()).isEqualTo(1);
        assertThat(update.getRemark()).isEqualTo("新描述");
    }

    @Test
    void updateRejectsZeroRowDatabaseUpdate() {
        DeviceType existing = deviceType(10L);
        existing.setTypeName("旧名称");
        existing.setTypeCode("OLD");
        when(deviceTypeMapper.selectOne(any())).thenReturn(existing);
        when(deviceTypeMapper.selectCount(any())).thenReturn(0L);
        when(deviceTypeMapper.updateById(any(DeviceType.class))).thenReturn(0);

        UpdateDeviceTypeRequest request = new UpdateDeviceTypeRequest();
        request.setId(10L);
        request.setTypeName("新名称");
        request.setTypeCode("NEW");
        request.setParentId(0L);
        request.setLevel(1);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(DeviceTypeException.class)
                .hasMessage("设备类型更新失败，请刷新后重试");
    }

    private DeviceType deviceType(Long id) {
        DeviceType deviceType = new DeviceType();
        deviceType.setId(id);
        deviceType.setParentId(0L);
        deviceType.setLevel(1);
        deviceType.setSort(0);
        deviceType.setStatus(1);
        deviceType.setIsDeleted(0);
        return deviceType;
    }
}
