package com.qst.smartbuildings.service;

import com.qst.smartbuildings.dto.DeviceAddDTO;
import com.qst.smartbuildings.dto.DeviceUpdateDTO;
import com.qst.smartbuildings.vo.CertificateUploadResult;
import com.qst.smartbuildings.vo.DeviceOnlineRateResult;
import com.qst.smartbuildings.vo.DeviceVO;
import com.qst.smartbuildings.vo.OnlineStatsResult;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.QrCodeResult;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;




/**
 * 设备台账服务接口。
 */
public interface AssetDeviceService {

    /**
     * 新增设备。
     * 校验类型/位置存在，系统自动生成唯一设备编码 device_code。
     *
     * @param dto 新增设备请求
     * @return 新设备ID
     */
    Long add(DeviceAddDTO dto);

    /**
     * 修改设备。
     * 仅修改请求体中非空的字段，若类型/位置发生变更则校验其有效性。
     *
     * @param dto 修改设备请求（id 必填）
     */
    void update(DeviceUpdateDTO dto);

    /**
     * 批量删除设备（逻辑删除）。
     *
     * @param ids 设备ID串，多个用逗号分隔，例如 "1,2,3"
     */
    void deleteDevices(String ids);

    /**
     * 修改设备启用状态。
     *
     * @param id           设备ID
     * @param enableStatus 启用状态：0-禁用，1-启用
     */
    void updateStatus(Long id, Integer enableStatus);

    /**
     * 获取设备二维码信息。
     *
     * @param id 设备ID
     * @return 二维码信息（设备编码、名称、二维码地址）
     */
    QrCodeResult getQrCode(Long id);

    /**
     * 设备在线率统计。
     *
     * @param locationId 位置ID，不传则全局统计（若指定则统计该位置及其子位置下的设备）
     * @return 在线率统计结果
     */
    OnlineStatsResult getOnlineStats(Long locationId);

    /**
     * 根据设备ID查询设备详情。
     *
     * @param id 设备ID
     * @return 设备详细信息
     */
    DeviceVO getDeviceById(Long id);


    /**
     * 分页查询设备列表（含塔吊等所有类型设备）。


     * 支持按设备编码、名称、类型（含子类型）、位置（含子位置）、在线状态、启用状态、品牌筛选。
     *
     * @param pageNum       当前页码
     * @param pageSize      每页条数
     * @param deviceCode    设备编码（精确）
     * @param deviceName    设备名称（模糊）
     * @param typeId        设备类型ID（含其子类型）
     * @param locationId    安装位置ID（含其子位置）
     * @param onlineStatus  在线状态：0-离线，1-在线
     * @param enableStatus  启用状态：0-禁用，1-启用
     * @param brand         品牌（模糊）
     * @return 分页结果
     */
    PageResult<DeviceVO> pageDevices(long pageNum, long pageSize, String deviceCode,
                                     String deviceName, Long typeId, Long locationId,
                                     Integer onlineStatus, Integer enableStatus, String brand);

    /**
     * 查询单设备在指定时间范围内的在线率。
     *
     * @param id        设备ID
     * @param beginTime 统计开始时间
     * @param endTime   统计结束时间
     * @return 在线率统计结果
     */
    DeviceOnlineRateResult getDeviceOnlineRate(Long id, LocalDateTime beginTime, LocalDateTime endTime);

    /**
     * 上传设备合格证附件。
     *
     * @param deviceId 设备ID
     * @param file     附件文件（图片/PDF）
     * @return 上传结果（附件访问地址）
     */
    CertificateUploadResult uploadCertificate(Long deviceId, MultipartFile file);
}


