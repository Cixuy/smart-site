package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteResponseData {
    private Integer successCount;
    private Integer failCount;
    private Integer totalCount;
    private List<Long> failedIds;
    private String aiLabelFilter;
    private String cameraName;
    private String recordTypeText;
    private String dateRange;

    public DeleteResponseData() {
    }

    public DeleteResponseData(int successCount, int failCount, int totalCount, List<Long> failedIds) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.totalCount = totalCount;
        this.failedIds = failedIds == null ? List.of() : failedIds;
    }
}
