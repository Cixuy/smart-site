package com.qst.smartbuildings.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ScreenVideoRotationListData {
    private List<ScreenVideoRotationVO> records;
}
