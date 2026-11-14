package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.ConfigExceptionHandler;
import com.qst.smartbuildings.config.ModelStorageProperties;
import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.dto.CreateDeviceTypeRequest;
import com.qst.smartbuildings.dto.UpdateDeviceTypeRequest;
import com.qst.smartbuildings.exception.DeviceTypeException;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.DeviceTypeService;
import com.qst.smartbuildings.vo.DeviceTypeTreeVO;
import com.qst.smartbuildings.vo.DeviceTypeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceTypeController.class)
@Import({SecurityConfig.class, ConfigExceptionHandler.class, ModelStorageProperties.class})
class DeviceTypeControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceTypeService deviceTypeService;

    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void treeReturnsOpenApiContainer() throws Exception {
        DeviceTypeTreeVO root = treeVo(1L, "大型机械", "MACHINE", 0L, 1);
        root.getChildren().add(treeVo(2L, "塔吊", "TOWER", 1L, 2));
        when(deviceTypeService.tree(1)).thenReturn(List.of(root));

        mockMvc.perform(get("/asset/type/tree").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tree[0].typeName").value("大型机械"))
                .andExpect(jsonPath("$.data.tree[0].children[0].typeCode").value("TOWER"));
    }

    @Test
    void listUsesTypeNameAndTypeCodeOnly() throws Exception {
        DeviceTypeVO vo = typeVo(10L, "塔吊", "LH-JZ-TD", 1L, 2);
        when(deviceTypeService.list("塔", "LH-JZ-TD", 1)).thenReturn(List.of(vo));

        mockMvc.perform(get("/asset/type/list")
                        .param("typeName", "塔")
                        .param("typeCode", "LH-JZ-TD")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].typeName").value("塔吊"))
                .andExpect(jsonPath("$.data.list[0].typeCode").value("LH-JZ-TD"))
                .andExpect(jsonPath("$.data.list[0].remark").value("测试备注"))
                .andExpect(jsonPath("$.data.list[0].name").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].code").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].description").doesNotExist());

        verify(deviceTypeService).list("塔", "LH-JZ-TD", 1);
    }

    @Test
    void detailReturnsDeviceType() throws Exception {
        when(deviceTypeService.detail(10L)).thenReturn(typeVo(10L, "塔吊", "LH-JZ-TD", 1L, 2));

        mockMvc.perform(get("/asset/type/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeName").value("塔吊"))
                .andExpect(jsonPath("$.data.typeCode").value("LH-JZ-TD"))
                .andExpect(jsonPath("$.data.remark").value("测试备注"))
                .andExpect(jsonPath("$.data.name").doesNotExist())
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andExpect(jsonPath("$.data.description").doesNotExist());
    }

    @Test
    void createAcceptsOpenApiPayload() throws Exception {
        when(deviceTypeService.create(any())).thenReturn(11L);

        mockMvc.perform(post("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":null,"typeName":"测试","parentId":1,"typeCode":"对对对","level":1,"sort":0,"status":1,"remark":"的","children":[],"name":"监控设备","description":"","code":"MONITOR","createTime":"2026-08-08T10:34:24"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<CreateDeviceTypeRequest> captor = ArgumentCaptor.forClass(CreateDeviceTypeRequest.class);
        verify(deviceTypeService).create(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getTypeName()).isEqualTo("测试");
        assertThat(captor.getValue().getTypeCode()).isEqualTo("对对对");
        assertThat(captor.getValue().getParentId()).isEqualTo(1L);
        assertThat(captor.getValue().getRemark()).isEqualTo("的");
    }

    @Test
    void createRejectsNonNullId() throws Exception {
        mockMvc.perform(post("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":10,"typeName":"塔吊","typeCode":"LH-JZ-TD","parentId":0,"level":1,"status":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数错误：id 新增设备类型时ID必须为空"));
    }

    @Test
    void updateAcceptsOpenApiPayloadAndBoundaryStatus() throws Exception {
        mockMvc.perform(put("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":10,"typeName":"塔吊改","typeCode":"LH-JZ-TD-2","parentId":0,"level":1,"sort":0,"status":0,"remark":"禁用","children":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void createRejectsLegacyAliasPayload() throws Exception {
        mockMvc.perform(post("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"塔吊","code":"LH-JZ-TD","parentId":0,"level":1,"description":"旧备注"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateAcceptsResponseShapedPayloadAndKeepsCanonicalValues() throws Exception {
        mockMvc.perform(put("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":1,"typeName":"监控设备改","typeCode":"MONITOR-NEW","parentId":0,"level":1,"sort":1,"status":1,"remark":"新描述","children":[],"code":"MONITOR","createTime":"2026-08-08T10:34:24","description":"","name":"监控设备"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<UpdateDeviceTypeRequest> captor = ArgumentCaptor.forClass(UpdateDeviceTypeRequest.class);
        verify(deviceTypeService).update(captor.capture());
        assertThat(captor.getValue().getTypeName()).isEqualTo("监控设备改");
        assertThat(captor.getValue().getTypeCode()).isEqualTo("MONITOR-NEW");
        assertThat(captor.getValue().getRemark()).isEqualTo("新描述");
    }

    @Test
    void updateStillRejectsUnknownPayloadField() throws Exception {
        mockMvc.perform(put("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":10,"unexpected":"value"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteParsesCommaSeparatedIds() throws Exception {
        mockMvc.perform(delete("/asset/type/10,11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(deviceTypeService).delete(List.of(10L, 11L));
    }

    @Test
    void invalidStatusQueryReturnsBusinessError() throws Exception {
        when(deviceTypeService.tree(2)).thenThrow(new DeviceTypeException("状态必须为0或1"));

        mockMvc.perform(get("/asset/type/tree").param("status", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("状态必须为0或1"));
    }

    @Test
    void malformedStatusQueryReturnsReadableError() throws Exception {
        mockMvc.perform(get("/asset/type/list").param("status", "enabled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数错误：status 应为 Integer 类型"));
    }

    @Test
    void malformedDetailIdReturnsReadableError() throws Exception {
        mockMvc.perform(get("/asset/type/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数错误：id 应为 Long 类型"));
    }

    @Test
    void createRejectsMissingRequiredName() throws Exception {
        mockMvc.perform(post("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"LH-JZ-TD","parentId":0,"level":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数错误：typeName 类型名称不能为空"));
    }

    @Test
    void createRejectsExtremeLengthAndLevel() throws Exception {
        String longName = "A".repeat(51);
        mockMvc.perform(post("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeName":"%s","typeCode":"LH-JZ-TD","parentId":0,"level":5}
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateRejectsMissingIdAndInvalidStatus() throws Exception {
        mockMvc.perform(put("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeName":"塔吊","status":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateRejectsLevelOutsideAllowedRange() throws Exception {
        mockMvc.perform(put("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":10,"level":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数错误：level 层级必须在1到4之间"));
    }

    @Test
    void deleteRejectsMalformedIds() throws Exception {
        mockMvc.perform(delete("/asset/type/10,a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("设备类型ID格式不正确"));
    }

    @Test
    void deleteReturnsBusinessErrorForChildrenOrLinkedDevices() throws Exception {
        doThrow(new DeviceTypeException("存在子节点的设备类型不可删除"))
                .when(deviceTypeService).delete(any());

        mockMvc.perform(delete("/asset/type/10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("存在子节点的设备类型不可删除"));
    }

    @Test
    void malformedJsonReturnsReadableError() throws Exception {
        mockMvc.perform(post("/asset/type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求体格式错误：请检查数字字段是否传入了字母或非法内容"));
    }

    private DeviceTypeVO typeVo(Long id, String typeName, String typeCode, Long parentId, Integer level) {
        DeviceTypeVO vo = new DeviceTypeVO();
        vo.setId(id);
        vo.setTypeName(typeName);
        vo.setTypeCode(typeCode);
        vo.setParentId(parentId);
        vo.setLevel(level);
        vo.setSort(1);
        vo.setStatus(1);
        vo.setRemark("测试备注");
        vo.setCreateTime(LocalDateTime.of(2026, 8, 8, 9, 0));
        return vo;
    }

    private DeviceTypeTreeVO treeVo(Long id, String typeName, String typeCode, Long parentId, Integer level) {
        DeviceTypeTreeVO vo = new DeviceTypeTreeVO();
        vo.setId(id);
        vo.setTypeName(typeName);
        vo.setTypeCode(typeCode);
        vo.setParentId(parentId);
        vo.setLevel(level);
        vo.setSort(1);
        vo.setStatus(1);
        return vo;
    }
}
