package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.AfterSalesType;
import com.county.vision.domain.enums.SettleDirection;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 配镜售后处理单（补助发放后）：
 * 家长退镜、只换片或只换镜架时，按门店实际退款与新发生费用重新计算补助差额，
 * 决定向家长追回或由平台补发，并始终与原补助/核验单连在一起。
 */
@Getter
@Setter
@Entity
@Table(name = "after_sales")
public class AfterSales extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 对应的同一副眼镜 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GlassesRecord glasses;

    /** 关联原核验单与原补助（售后必须挂在已发放的原补助上） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SubsidyVerification verification;

    @ManyToOne(fetch = FetchType.LAZY)
    private SubsidyApplication subsidy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AfterSalesType type;

    /** 家长申请/门店受理日期、门店处理完成日期 */
    private LocalDate applyDate;
    private LocalDate processedDate;

    /** 门店实际退款金额（元，退镜=全额退；换片/换架=旧件折退部分） */
    private BigDecimal refundAmount;

    /** 新发生费用（元，换片=新镜片费用；换架=新镜架费用；退镜为 0） */
    private BigDecimal newCostAmount;

    /** 售后结算后这副眼镜对应的自付费用基数（原费用 - 退款 + 新费用） */
    private BigDecimal settledCostAmount;

    /** 原补助金额快照（元） */
    private BigDecimal originalSubsidy;

    /** 重新核定的应补助金额（元） */
    private BigDecimal recalculatedSubsidy;

    /** 差额（元，正数） */
    private BigDecimal settleAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SettleDirection direction = SettleDirection.PENDING;

    /** REQUESTED 已受理待结算 / SETTLED 差额已结清 */
    private String status = "REQUESTED";

    /** 差额实际追回/补发凭证号与时间 */
    private String settleVoucherNo;
    private LocalDate settleDate;

    private String handler;

    @Column(length = 500)
    private String remark;
}
