package com.county.vision.domain.enums;

/**
 * 售后结算方向：依据门店实际退款与新费用，对原补助进行追回或补发
 */
public enum SettleDirection {
    RECOVER("应追回"),
    SUPPLEMENT("应补发"),
    NONE("无需差额"),
    PENDING("待结算");

    private final String label;

    SettleDirection(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
