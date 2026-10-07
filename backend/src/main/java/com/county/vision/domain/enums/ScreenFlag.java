package com.county.vision.domain.enums;

/**
 * 筛查现场异常标记（可多选，以字符串集合存储）
 */
public enum ScreenFlag {
    ABSENT("筛查请假"),
    UNREACHABLE("家长电话无法接通"),
    RETEST_NEEDED("需要复测"),
    DATA_CONFLICT("复诊与筛查差异较大"),
    EXTERNAL_GLASSES("家长自行外配眼镜"),
    SUBSIDY_DISPUTE("补助资格争议"),
    GLASSES_DISCOMFORT("配镜后不适"),
    REVIEW_NO_SHOW("半年复查未到场"),
    IMPORT_ERROR("批量导入信息错误"),
    DISPENSE_SUSPENDED("门店配镜核验挂起"),
    SUBSIDY_SETTLE("补助售后差额待结算");

    private final String label;

    ScreenFlag(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
