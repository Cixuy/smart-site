package com.qst.smartbuildings.service;

import com.qst.smartbuildings.dto.ScreenConfigSaveRequest;
import com.qst.smartbuildings.dto.ScreenVideoRotationSaveRequest;
import com.qst.smartbuildings.vo.ScreenConfigListData;
import com.qst.smartbuildings.vo.ScreenConfigVO;
import com.qst.smartbuildings.vo.ScreenRealtimeData;
import com.qst.smartbuildings.vo.ScreenVideoRotationListData;

import java.util.List;

public interface ScreenService {
    ScreenConfigListData listConfigs(Long projectId, Integer status);
    Long createConfig(ScreenConfigSaveRequest request);
    ScreenConfigVO getConfig(Long screenId);
    boolean updateConfig(Long screenId, ScreenConfigSaveRequest request);
    boolean deleteConfig(Long screenId);
    boolean setDefaultScreen(Long screenId);
    ScreenRealtimeData getRealtimeData(Long screenId);
    ScreenVideoRotationListData listVideoRotations(Long screenId);
    boolean saveVideoRotations(Long screenId, List<ScreenVideoRotationSaveRequest> request);
}
