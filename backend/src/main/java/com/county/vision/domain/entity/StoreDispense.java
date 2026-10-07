package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.DispenseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合作门店配镜核验单：门店上传处方、镜片参数、付款凭证、取镜签收，
 * 平台对照最近一次复诊处方、家庭资格与补助标准逐项核验后，方可发放补助。
 */
@Getter
@Setter
@Entity
@Table(name = "store_dispense")
public class StoreDispense extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 关联的配镜记录（用于确认“同一副眼镜”） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GlassesRecord glasses;

    /** 合作门店名称 */
    private String storeName;

    /** 门店上传处方参数（应与最近一次复诊处方一致） */
    private String rxSphereLeft;
    private String rxSphereRight;
    private String rxCylinderLeft;
    private String rxCylinderRight;
    private String rxAxisLeft;
    private String rxAxisRight;
    private String pd;

    /** 镜片参数（折射率/品牌/功能等） */
    @Column(length = 300)
    private String lensParams;

    /** 镜片费用 */
    private BigDecimal lensAmount;

    /** 镜架费用 */
    private BigDecimal frameAmount;

    /** 实收合计 */
    private BigDecimal totalAmount;

    /** 付款凭证号（平台查重，防止同一凭证重复申领） */
    private String paymentVoucherNo;

    /** 取镜签收 */
    private Boolean pickupSigned = false;
    private LocalDate pickupDate;
    private String pickupSigner;

    @Enumerated(EnumType.STRING)
    private DispenseStatus status = DispenseStatus.PENDING;

    /** 逐项核验结果明细（每项一行：√/× + 说明） */
    @Column(length = 1500)
    private String verifyDetail;

    /** 挂起原因（处方不一致/参数缺失/凭证重复/重复提交等） */
    @Column(length = 500)
    private String suspendReason;

    private String verifiedBy;
    private LocalDateTime verifiedAt;

    /** 按补助标准计算的应补金额 */
    private BigDecimal standardAmount;

    /** 核验通过后关联的补助申请（发放与售后都挂在它上面） */
    @ManyToOne(fetch = FetchType.LAZY)
    private SubsidyApplication subsidy;
}
