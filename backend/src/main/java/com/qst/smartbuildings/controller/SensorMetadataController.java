package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.SensorMetadataService;
import com.qst.smartbuildings.vo.SensorMetaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sensor")
@Tag(name = "实时推流接口/传感器推流", description = "传感器推流相关 HTTP 接口（元数据列表等）")
public class SensorMetadataController {
    private final SensorMetadataService service;

    public SensorMetadataController(SensorMetadataService service) {
        this.service = service;
    }

    @Operation(summary = "获取传感器元数据列表",
            description = "前端在建立 WebSocket 连接前/后调用，获取传感器的静态配置信息（名称、阈值、父节点等），"
                    + "后续 WebSocket 仅推送精简字段，前端通过 id 匹配此列表中的元数据进行渲染。")
    @GetMapping("/list")
    public Result<List<SensorMetaVO>> list(@RequestParam(required = false) Long parentId,
                                           @RequestParam(required = false) Integer parentType) {
        return Result.ok(service.list(parentId, parentType));
    }
}