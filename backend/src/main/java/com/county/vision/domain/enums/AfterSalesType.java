package com.county.vision.domain.enums;

/**
 * 配镜售后处理类型
 */
public enum AfterSalesType {
    RETURN("退镜"),
    LENS_CHANGE("更换镜片"),
    FRAME_CHANGE("更换镜架"),
    ADJUST("不适调整");

    private final String label;

    AfterSalesType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
