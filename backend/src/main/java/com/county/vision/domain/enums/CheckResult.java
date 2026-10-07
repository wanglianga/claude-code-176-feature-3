package com.county.vision.domain.enums;

/**
 * 补助核验逐项比对结果
 */
public enum CheckResult {
    PASS("通过"),
    FAIL("不符"),
    MISSING("缺失"),
    PENDING("待核");

    private final String label;

    CheckResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
