package com.county.vision.domain.enums;

/**
 * 补助审批状态
 */
public enum SubsidyStatus {
    NOT_ELIGIBLE("不符合"),
    ELIGIBLE("符合待申请"),
    APPLIED("已申请"),
    APPROVED("已核准发放"),
    REJECTED("争议驳回"),
    DISPUTE("争议复核中"),
    PAID("补助已发放"),
    RECOVERED("补助已追回");

    private final String label;

    SubsidyStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
