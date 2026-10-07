package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.CheckResult;
import com.county.vision.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合作门店配镜补助发放核验单：一副眼镜一张单。
 * 平台把门店上传的处方/镜片参数/付款凭证/取镜签收，与最近一次复诊处方、家庭补助资格、
 * 县域补助标准逐项比对；任一项不符、缺失或存疑即挂起（SUSPENDED），不能直接发放补助。
 */
@Getter
@Setter
@Entity
@Table(name = "subsidy_verification",
        uniqueConstraints = @UniqueConstraint(name = "uk_verification_glasses", columnNames = "glasses_id"))
public class SubsidyVerification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 同一副眼镜：核验单与配镜记录一一对应 */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private GlassesRecord glasses;

    /** 发放时自动关联/建立的补助申请 */
    @ManyToOne(fetch = FetchType.LAZY)
    private SubsidyApplication subsidy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VerificationStatus status = VerificationStatus.PENDING;

    /* ---------------- 逐项比对结论 ---------------- */

    /** 同一学生核验：学生身份/学籍与处方、签收人一致 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult studentCheck = CheckResult.PENDING;

    /** 同一副眼镜核验：门店处方与最近一次复诊处方逐项一致（球镜/柱镜/轴位/瞳距） */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult prescriptionCheck = CheckResult.PENDING;

    /** 镜片加工参数完整且与处方一致 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult lensParamCheck = CheckResult.PENDING;

    /** 家庭补助资格核验（在管理链路上、医生初判符合、无在挂起的异常） */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult eligibilityCheck = CheckResult.PENDING;

    /** 付款凭证真实且未被其他核验单使用 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult voucherCheck = CheckResult.PENDING;

    /** 取镜已签收 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult pickupCheck = CheckResult.PENDING;

    /** 补助标准核验：金额、机构合规、无重复补助 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CheckResult standardCheck = CheckResult.PENDING;

    /** 挂起原因（多条以“；”分隔），如：处方不一致/参数缺失/付款凭证重复/门店重复提交 */
    @Column(length = 1000)
    private String holdReasons;

    /** 逐项比对明细留痕 */
    @Column(length = 2000)
    private String checkDetail;

    /* ---------------- 金额 ---------------- */

    /** 适用补助标准上限（元） */
    private BigDecimal standardAmount;
    /** 核定应发金额（元） */
    private BigDecimal payableAmount;
    /** 实际发放金额（元，发放后写入） */
    private BigDecimal paidAmount;

    /* ---------------- 流转留痕 ---------------- */

    private String submitter;
    private LocalDateTime submitTime;
    private String reviewer;
    private LocalDateTime reviewTime;
    private LocalDateTime paidTime;

    @Column(length = 500)
    private String opinion;
}
