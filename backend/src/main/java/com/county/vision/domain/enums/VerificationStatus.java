package com.county.vision.domain.enums;

/**
 * 合作门店配镜补助发放核验状态。
 * 材料不齐或比对存疑时一律先挂起（SUSPENDED），不得直接发放，杜绝重复补助。
 */
public enum VerificationStatus {
    PENDING("待核验"),
    PASSED("核验通过待发放"),
    SUSPENDED("挂起待复核"),
    PAID("补助已发放"),
    REJECTED("核验驳回"),
    AFTER_SALES("售后处理中"),
    CLOSED("售后结清");

    private final String label;

    VerificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
