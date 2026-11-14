package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.CreateDeviceTypeRequest;
import com.qst.smartbuildings.dto.UpdateDeviceTypeRequest;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.DeviceTypeService;
import com.qst.smartbuildings.vo.DeviceTypeListData;
import com.qst.smartbuildings.vo.DeviceTypeTreeData;
import com.qst.smartbuildings.vo.DeviceTypeTreeVO;
import com.qst.smartbuildings.vo.DeviceTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "设备类型管理")
@RestController
@RequestMapping("/asset/type")
public class DeviceTypeController {
    private final DeviceTypeService deviceTypeService;

    public DeviceTypeController(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    @Operation(summary = "查询设备类型树")
    @GetMapping("/tree")
    public Result<DeviceTypeTreeData> tree(@RequestParam(required = false) Integer status) {
        List<DeviceTypeTreeVO> tree = deviceTypeService.tree(status);
        return Result.ok(new DeviceTypeTreeData(tree));
    }

    @Operation(summary = "查询设备类型列表(平铺)")
    @GetMapping("/list")
    public Result<DeviceTypeListData> list(
            @RequestParam(required = false) String typeName,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) Integer status) {
        return Result.ok(new DeviceTypeListData(deviceTypeService.list(typeName, typeCode, status)));
    }

    @Operation(summary = "查询设备类型详情")
    @GetMapping("/{id}")
    public Result<DeviceTypeVO> detail(@PathVariable Long id) {
        return Result.ok(deviceTypeService.detail(id));
    }

    @Operation(summary = "新增设备类型")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateDeviceTypeRequest request) {
        deviceTypeService.create(request);
        return Result.ok();
    }

    @Operation(summary = "修改设备类型")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateDeviceTypeRequest request) {
        deviceTypeService.update(request);
        return Result.ok();
    }

    @Operation(summary = "删除设备类型(批量)")
    @DeleteMapping("/{ids}")
    public Result<Void> delete(@PathVariable String ids) {
        deviceTypeService.delete(parseIds(ids));
        return Result.ok();
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            throw new com.qst.smartbuildings.exception.DeviceTypeException("设备类型ID不能为空");
        }
        try {
            return Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException exception) {
            throw new com.qst.smartbuildings.exception.DeviceTypeException("设备类型ID格式不正确");
        }
    }
}
