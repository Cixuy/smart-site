package com.qst.smartbuildings.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页响应（与前端约定字段：total / pageNum / pageSize / list）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageListVO<T> {

    /** 总条数 */
    private long total;

    /** 当前页码 */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /** 数据列表 */
    private List<T> list;

    public static <T> PageListVO<T> of(Page<?> page, List<T> list) {
        return new PageListVO<>(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    public static <T> PageListVO<T> of(long total, long pageNum, long pageSize, List<T> list) {
        return new PageListVO<>(total, pageNum, pageSize, list);
    }
}