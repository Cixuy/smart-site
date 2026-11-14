package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.LocationAddDTO;
import com.qst.smartbuildings.dto.LocationUpdateDTO;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.AssetLocationException;
import com.qst.smartbuildings.service.AssetLocationService;
import com.qst.smartbuildings.vo.LocationMapVO;
import com.qst.smartbuildings.vo.LocationTreeVO;
import com.qst.smartbuildings.vo.LocationVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 设备位置管理
 */
@RestController
@RequestMapping("/asset/location")
public class AssetLocationController {

    private final AssetLocationService assetLocationService;

    public AssetLocationController(AssetLocationService assetLocationService) {
        this.assetLocationService = assetLocationService;
    }

    /**
     * 查询位置树
     */
    @GetMapping("/tree")
    public Result<Map<String, List<LocationTreeVO>>> tree(
            @RequestParam(required = false) Integer status) {
        return Result.ok(Map.of("tree", assetLocationService.tree(status)));
    }

    /**
     * 查询位置列表（平铺）
     */
    @GetMapping("/list")
    public Result<Map<String, List<LocationVO>>> list(
            @RequestParam(required = false) String locationName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long responsibleUserId) {
        return Result.ok(Map.of("list", assetLocationService.list(locationName, status, responsibleUserId)));
    }

    /**
     * 查询位置详情
     */
    @GetMapping("/{id}")
    public Result<LocationVO> detail(@PathVariable Long id) {
        return Result.ok(assetLocationService.detail(id));
    }

    /**
     * 新增位置
     */
    @PostMapping
    public Result<Map<String, Long>> add(@Valid @RequestBody LocationAddDTO request) {
        return Result.ok(Map.of("id", assetLocationService.add(request)));
    }

    /**
     * 修改位置
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody LocationUpdateDTO request) {
        assetLocationService.update(request);
        return Result.ok();
    }

    /**
     * 删除位置（批量）
     */
    @DeleteMapping("/{ids}")
    public Result<Void> delete(@PathVariable String ids) {
        List<Long> locationIds;
        try {
            locationIds = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException ex) {
            throw new AssetLocationException("位置ID格式不正确");
        }
        assetLocationService.delete(locationIds);
        return Result.ok();
    }

    /**
     * 获取 GIS 坐标数据（地图/3D打点）
     */
    @GetMapping("/map")
    public Result<Map<String, List<LocationMapVO>>> map() {
        return Result.ok(Map.of("list", assetLocationService.mapData()));
    }

    @ExceptionHandler(AssetLocationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleAssetLocationException(AssetLocationException exception) {
        return Result.error(400, exception.getMessage());
    }
}
