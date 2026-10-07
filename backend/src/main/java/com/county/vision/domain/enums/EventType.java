package com.county.vision.domain.enums;

/**
 * 健康管理时间线事件类型，覆盖筛查→告知→复诊→处方→配镜→补助→复查→学校干预全链条
 */
public enum EventType {
    IMPORT("名单导入"),
    SCREEN("视力筛查"),
    NOTIFY("家长告知"),
    PARENT_CONFIRM("家长确认"),
    SCHOOL_REVIEW("学校复核"),
    RETEST("复测"),
    VISIT("医院复诊"),
    PRESCRIPTION("处方出具"),
    GLASSES("配镜"),
    SUBSIDY("配镜补助"),
    VERIFICATION("发放核验"),
    AFTER_SALES("售后差额"),
    COMFORT("不适与回访"),
    REVIEW("复查随访"),
    INTERVENTION("学校干预"),
    ISSUE("异常事项登记"),
    RESOLVE("异常事项处理"),
    STAGE_CHANGE("阶段流转");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
