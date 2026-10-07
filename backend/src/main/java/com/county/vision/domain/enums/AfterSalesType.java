package com.county.vision.domain.enums;

/**
 * 配镜售后类型：退镜（整副退）/ 换片（仅更换镜片）/ 换架（仅更换镜架）
 */
public enum AfterSalesType {
    REFUND("退镜退款"),
    LENS_REPLACE("换片"),
    FRAME_REPLACE("仅更换镜架");

    private final String label;

    AfterSalesType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
