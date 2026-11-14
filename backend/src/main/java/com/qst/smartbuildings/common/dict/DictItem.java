package com.qst.smartbuildings.common.dict;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 数据字典条目模型
 * <p>
 * 用于向前端下拉框 / 筛选条件 / 枚举展示提供统一的字典数据。
 * 每个字典条目包含：字典类型、字典标签、字典值、排序、样式与备注。
 * </p>
 */
@Schema(description = "数据字典条目")
public class DictItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字典类型编码（如 alarm_level / notification_type）", example = "alarm_level")
    private String dictType;

    @Schema(description = "字典标签（展示文本）", example = "预警")
    private String label;

    @Schema(description = "字典值", example = "1")
    private String value;

    @Schema(description = "排序号", example = "1")
    private Integer sort;

    @Schema(description = "字典值类型：number / string", example = "number")
    private String valueType;

    @Schema(description = "CSS 样式 / 颜色标识（如 warning、danger）", example = "warning")
    private String tagType;

    @Schema(description = "备注说明", example = "黄色闪烁，无声音提示")
    private String remark;

    public DictItem() {
    }

    public DictItem(String dictType, String label, String value, Integer sort) {
        this.dictType = dictType;
        this.label = label;
        this.value = value;
        this.sort = sort;
        this.valueType = "string";
    }

    public DictItem(String dictType, String label, String value, Integer sort, String tagType) {
        this(dictType, label, value, sort);
        this.tagType = tagType;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getTagType() {
        return tagType;
    }

    public void setTagType(String tagType) {
        this.tagType = tagType;
    }

    public String getRemark() {
        return remark;
    }

    public DictItem setRemark(String remark) {
        this.remark = remark;
        return this;
    }
}
