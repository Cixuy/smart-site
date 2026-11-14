package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.DeviceAddDTO;
import com.qst.smartbuildings.dto.DeviceStatusDTO;
import com.qst.smartbuildings.dto.DeviceUpdateDTO;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.AssetDeviceException;
import com.qst.smartbuildings.service.AssetDeviceService;
import com.qst.smartbuildings.vo.CertificateUploadResult;
import com.qst.smartbuildings.vo.DeviceOnlineRateResult;
import com.qst.smartbuildings.vo.DeviceVO;
import com.qst.smartbuildings.vo.OnlineStatsResult;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.QrCodeResult;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 设备台账管理：分页查询设备列表、新增设备、修改设备、删除设备。
 */
@RestController
@RequestMapping("/asset/device")
public class AssetDeviceController {

    private final AssetDeviceService assetDeviceService;

    public AssetDeviceController(AssetDeviceService assetDeviceService) {

        this.assetDeviceService = assetDeviceService;
    }

    /**
     * 分页查询设备列表。
     * 支持按设备类型、安装位置（工地/楼栋/作业面）、在线状态、启用状态、品牌等多维度筛选。
     * 类型筛选塔吊即可获得塔吊设备列表。
     *
     * @param pageNum      当前页码，默认1，最小1
     * @param pageSize     每页条数，默认10，最小1，最大100
     * @param deviceCode   设备编码（精确）
     * @param deviceName   设备名称（模糊）
     * @param typeId       设备类型ID（传塔吊类型ID可筛选所有塔吊，含子类型）
     * @param locationId   安装位置ID（支持按工地/楼栋/作业面筛选，含子位置）
     * @param onlineStatus 在线状态：0-离线，1-在线
     * @param enableStatus 启用状态：0-禁用，1-启用
     * @param brand        品牌（模糊）
     */
    @GetMapping("/page")
    public Result<PageResult<DeviceVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Integer onlineStatus,
            @RequestParam(required = false) Integer enableStatus,
            @RequestParam(required = false) String brand) {
        return Result.ok(assetDeviceService.pageDevices(pageNum, pageSize, deviceCode,
                deviceName, typeId, locationId, onlineStatus, enableStatus, brand));
    }

    /**
     * 新增设备。
     * device_code 由系统自动生成（类型编码+时间戳+序号），校验类型与安装位置有效性。
     */
    @PostMapping
    public Result<Map<String, Long>> add(@Valid @RequestBody DeviceAddDTO request) {
        return Result.ok(Map.of("id", assetDeviceService.add(request)));
    }

    /**
     * 修改设备。
     * 仅修改请求体中非空的字段（id 必填），若类型/位置发生变更则校验其有效性。
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody DeviceUpdateDTO request) {
        assetDeviceService.update(request);
        return Result.ok();
    }

    /**
     * 根据设备ID查询设备详情。
     *
     * @param id 设备ID
     * @return 设备详细信息
     */
    @GetMapping("/{id}")
    public Result<DeviceVO> getDevice(@PathVariable Long id) {
        // 需要在 AssetDeviceService 中定义 getDeviceById 方法
        return Result.ok(assetDeviceService.getDeviceById(id));
    }

    /**
     * 删除设备（批量，逻辑删除）。
     * ids 为逗号分隔的设备ID串，例如 "1,2,3"。
     */
    @DeleteMapping("/{ids}")
    public Result<Void> delete(@PathVariable String ids) {
        assetDeviceService.deleteDevices(ids);
        return Result.ok();
    }

    /**
     * 修改设备启用状态。
     * 请求体仅需 enableStatus：0-禁用，1-启用。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody DeviceStatusDTO request) {
        assetDeviceService.updateStatus(id, request.getEnableStatus());
        return Result.ok();
    }

    /**
     * 获取设备二维码信息（设备编码、名称、二维码图片地址）。
     */
    @GetMapping("/{id}/qrCode")
    public Result<QrCodeResult> getQrCode(@PathVariable Long id) {
        return Result.ok(assetDeviceService.getQrCode(id));
    }

    /**
     * 设备在线率统计。
     * 不传 locationId 则全局统计；传入则按该位置及其子位置下的设备统计。
     *
     * @param locationId 位置ID（可空）
     */
    @GetMapping("/onlineStats")
    public Result<OnlineStatsResult> getOnlineStats(@RequestParam(required = false) Long locationId) {
        return Result.ok(assetDeviceService.getOnlineStats(locationId));
    }

    /**
     * 查询单设备在指定时间范围内的在线率。
     *
     * @param id        设备ID
     * @param beginTime 统计开始时间（ISO格式，如 2026-01-01 00:00:00）
     * @param endTime   统计结束时间
     */
    @GetMapping("/{id}/onlineRate")
    public Result<DeviceOnlineRateResult> getDeviceOnlineRate(
            @PathVariable Long id,
            @RequestParam LocalDateTime beginTime,
            @RequestParam LocalDateTime endTime) {
        return Result.ok(assetDeviceService.getDeviceOnlineRate(id, beginTime, endTime));
    }

    /**
     * 上传设备合格证附件。
     *
     * @param file     附件文件（图片/PDF）
     * @param deviceId 设备ID
     * @return 上传结果（附件访问地址）
     */
    @PostMapping("/upload/certificate")
    public Result<CertificateUploadResult> uploadCertificate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("deviceId") Long deviceId) {
        return Result.ok(assetDeviceService.uploadCertificate(deviceId, file));
    }

    @ExceptionHandler(AssetDeviceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleAssetDeviceException(AssetDeviceException exception) {
        return Result.error(400, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldError();

        return Result.error(400, error == null ? "请求参数不正确" : error.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return Result.error(400, "请求体格式不正确，请检查JSON内容和字段类型");
    }
}
