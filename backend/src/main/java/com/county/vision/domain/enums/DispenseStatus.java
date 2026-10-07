package com.county.vision.domain.enums;

/**
 * 合作门店配镜核验单状态
 */
public enum DispenseStatus {
    PENDING("待核验"),
    PASSED("核验通过"),
    SUSPENDED("核验挂起");

    private final String label;

    DispenseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
