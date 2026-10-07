package com.county.vision.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合作门店配镜补助发放核验、售后差额结算、配镜不适调整与适应回访相关入参。
 */
public class VerificationDtos {

    /** 合作门店上传：配镜材料（处方 / 镜片加工参数 / 付款凭证 / 取镜签收）并提交发放核验 */
    @Data
    public static class StoreGlassesRequest {
        /** 为空表示新登记配镜并建立核验单；传 glassesId 表示对挂起单补材料后重新提交 */
        private Long glassesId;
        /** 新登记时必填；补材料重提时可省略（以原配镜记录学生为准） */
        private Long studentId;
        private String opticianOrg;
        private LocalDate glassesDate;
        private BigDecimal amount;
        private String lensBrand;

        /* 材料1：验光处方 */
        private String rxNo;
        private String rxSphereLeft;
        private String rxSphereRight;
        private String rxCylinderLeft;
        private String rxCylinderRight;
        private String rxAxisLeft;
        private String rxAxisRight;
        private String rxPd;

        /* 材料2：镜片实际加工参数 */
        private String lensSphereLeft;
        private String lensSphereRight;
        private String lensCylinderLeft;
        private String lensCylinderRight;
        private String lensAxisLeft;
        private String lensAxisRight;
        private String lensPd;

        /* 材料3：付款凭证 */
        private String paymentVoucherNo;
        private String paymentVoucherFile;

        /* 材料4：取镜签收 */
        private Boolean pickedUp;
        private LocalDate pickupDate;
        private String receiverName;
        private String pickupRemark;
    }

    /** 卫健部门对挂起核验单的人工复核：RELEASE 放行 / REJECT 驳回 */
    @Data
    public static class VerificationReviewRequest {
        @NotNull
        private Long verificationId;
        /** RELEASE 放行 / REJECT 驳回 */
        private String action;
        /** 放行时可调整核定发放金额（不高于补助标准上限） */
        private BigDecimal payableAmount;
        private String opinion;
    }

    /** 家长退镜 / 换片 / 只换镜架：门店登记实际退款与新费用，平台重算差额 */
    @Data
    public static class AfterSalesRequest {
        @NotNull
        private Long glassesId;
        /** REFUND 退镜退款 / LENS_REPLACE 换片 / FRAME_REPLACE 仅换架 */
        private String type;
        private LocalDate applyDate;
        private LocalDate processedDate;
        /** 门店实际退款金额 */
        private BigDecimal refundAmount;
        /** 新发生费用（换片/换架） */
        private BigDecimal newCostAmount;
        private String remark;
        /** true=差额当场已结清（追回/补发凭证一并登记） */
        private Boolean settledNow;
        private String settleVoucherNo;
    }

    /** 售后差额实际追回/补发结清 */
    @Data
    public static class AfterSalesSettleRequest {
        @NotNull
        private Long afterSalesId;
        private String settleVoucherNo;
        private LocalDate settleDate;
        private String remark;
    }

    /** 学生反馈配镜不适（头晕/看不清/镜架不合适） */
    @Data
    public static class DiscomfortFeedbackRequest {
        @NotNull
        private Long glassesId;
        /** DIZZY / BLURRY / FRAME_FIT / OTHER */
        private String discomfortType;
        private LocalDate feedbackDate;
        private String feedback;
    }

    /** 门店上传不适调整结果 */
    @Data
    public static class AdjustResultRequest {
        @NotNull
        private Long followupId;
        private String adjustResult;
        private LocalDate adjustDate;
        /** 是否建议返院复查 */
        private Boolean reviewAdvised;
        private LocalDate reviewPlannedDate;
    }

    /** 适应回访结果登记 */
    @Data
    public static class CallbackRequest {
        @NotNull
        private Long followupId;
        private LocalDate callbackDate;
        private String callbackResult;
        /** 调整/适应后是否已无不适应 */
        private Boolean resolved;
    }
}
