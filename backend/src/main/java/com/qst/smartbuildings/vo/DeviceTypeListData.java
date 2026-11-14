package com.qst.smartbuildings.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DeviceTypeListData {
    private List<DeviceTypeVO> list;
}
