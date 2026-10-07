package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.AfterSalesType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 配镜售后处理：退镜/换片/换架引起的补助差额重算（追回或补发），
 * 以及学生不适反馈后的门店调整记录；始终与原补助申请关联。
 */
@Getter
@Setter
@Entity
@Table(name = "after_sales")
public class AfterSales extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 关联的门店核验单 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StoreDispense dispense;

    /** 原补助申请（售后处理与原补助连在一起） */
    @ManyToOne(fetch = FetchType.LAZY)
    private SubsidyApplication subsidy;

    @Enumerated(EnumType.STRING)
    private AfterSalesType type;

    private LocalDate handleDate;

    /** 退镜/换片/换架原因，或学生不适反馈（头晕/看不清/镜架不合适） */
    @Column(length = 500)
    private String reason;

    /** 实际退款金额（退镜/换片差价退回） */
    private BigDecimal refundAmount;

    /** 售后后的新费用合计（换片/换架后重新计价） */
    private BigDecimal newAmount;

    /** 原补助金额 */
    private BigDecimal oldSubsidy;

    /** 按补助标准重算的应补金额 */
    private BigDecimal newSubsidy;

    /** 差额：正数=应补发，负数=应追回 */
    private BigDecimal difference;

    /** 门店上传的调整结果（不适调整类售后） */
    @Column(length = 500)
    private String adjustResult;

    /**
     * PENDING_SETTLE 待结算 / SETTLED 已结算 /
     * WAIT_ADJUST 待门店调整 / ADJUSTED 已调整（适应回访中）
     */
    private String status;

    /** 处理人（结算确认人/调整登记人） */
    private String handler;
}
