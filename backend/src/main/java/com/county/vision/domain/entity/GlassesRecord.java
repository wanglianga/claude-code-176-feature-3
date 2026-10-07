package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 配镜记录（合作门店配镜 或 家长自行外配登记）。
 *  合作门店发放补助前须上传：处方、镜片加工参数、付款凭证、取镜签收四类材料。 */
@Getter
@Setter
@Entity
@Table(name = "glasses_record")
public class GlassesRecord extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    private String opticianOrg;
    private LocalDate glassesDate;

    /** 是否合作门店配镜（false=家长自行外配） */
    private Boolean partnerOptician = true;

    private BigDecimal amount;

    /** 处方/镜片信息摘要（兼容旧数据） */
    @Column(length = 300)
    private String lensInfo;

    /* ---------------- 门店上传材料 1：验光处方（与最近一次复诊处方逐项比对） ---------------- */

    /** 处方编号 / 处方来源机构 */
    private String rxNo;
    private String rxSphereLeft;
    private String rxSphereRight;
    private String rxCylinderLeft;
    private String rxCylinderRight;
    private String rxAxisLeft;
    private String rxAxisRight;
    private String rxPd;

    /* ---------------- 门店上传材料 2：镜片实际加工参数 ---------------- */

    /** 镜片品牌/品类（如 1.56 防蓝光） */
    private String lensBrand;
    private String lensSphereLeft;
    private String lensSphereRight;
    private String lensCylinderLeft;
    private String lensCylinderRight;
    private String lensAxisLeft;
    private String lensAxisRight;
    private String lensPd;

    /* ---------------- 门店上传材料 3：付款凭证 ---------------- */

    /** 付款凭证号（发票/收据号，全局查重，防同一凭证领两次补助） */
    private String paymentVoucherNo;
    /** 凭证影像文件名（演示环境只存名称/说明） */
    @Column(length = 300)
    private String paymentVoucherFile;

    /* ---------------- 门店上传材料 4：取镜签收 ---------------- */

    /** 是否已取镜并签收 */
    private Boolean pickedUp = false;
    private LocalDate pickupDate;
    /** 签收人（家长姓名）与签收方式说明（本人签字/家长代签） */
    private String receiverName;
    @Column(length = 300)
    private String pickupRemark;

    /* ---------------- 配镜不适 ---------------- */

    /** 配镜后是否不适 */
    private Boolean discomfort = false;
    /** 不适类型：DIZZY 头晕 / BLURRY 看不清 / FRAME_FIT 镜架不合适 / OTHER */
    private String discomfortType;
    @Column(length = 500)
    private String discomfortDetail;

    /** 是否已处理不适（门店调整/复诊） */
    private Boolean discomfortResolved = false;
}
