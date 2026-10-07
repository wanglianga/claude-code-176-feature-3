package com.county.vision.domain.enums;

/**
 * 学生配镜不适反馈类型（头晕 / 看不清 / 镜架不合适）
 */
public enum DiscomfortType {
    DIZZY("头晕"),
    BLURRY("看不清"),
    FRAME_FIT("镜架不合适"),
    OTHER("其他不适");

    private final String label;

    DiscomfortType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
