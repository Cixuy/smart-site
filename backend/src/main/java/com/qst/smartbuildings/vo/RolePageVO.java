package com.qst.smartbuildings.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RolePageVO {
    private long total;
    private long pages;
    private long pageNum;
    private long pageSize;
    private List<RoleVO> records;
}
