package com.county.vision.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合作门店配镜核验与售后相关请求 */
public class VerifyDtos {

    @Data
    public static class DispenseSubmitRequest {
        @NotNull(message = "学生ID不能为空")
        private Long studentId;
        @NotNull(message = "配镜记录ID不能为空")
        private Long glassesId;
        private String storeName;
        private String rxSphereLeft;
        private String rxSphereRight;
        private String rxCylinderLeft;
        private String rxCylinderRight;
        private String rxAxisLeft;
        private String rxAxisRight;
        private String pd;
        private String lensParams;
        private BigDecimal lensAmount;
        private BigDecimal frameAmount;
        private BigDecimal totalAmount;
        private String paymentVoucherNo;
        private Boolean pickupSigned;
        private LocalDate pickupDate;
        private String pickupSigner;
    }

    @Data
    public static class AfterSalesRequest {
        @NotNull(message = "核验单ID不能为空")
        private Long dispenseId;
        /** RETURN 退镜 / LENS_CHANGE 更换镜片 / FRAME_CHANGE 更换镜架 */
        @NotNull(message = "售后类型不能为空")
        private String type;
        private String reason;
        private BigDecimal refundAmount;
        /** 售后后的新费用合计（换片/换架必填） */
        private BigDecimal newAmount;
    }

    @Data
    public static class FeedbackRequest {
        @NotNull(message = "核验单ID不能为空")
        private Long dispenseId;
        /** 学生不适反馈：头晕 / 看不清 / 镜架不合适 等 */
        private String feedback;
    }

    @Data
    public static class AdjustResultRequest {
        @NotNull(message = "售后记录ID不能为空")
        private Long afterSalesId;
        /** 门店上传的调整结果 */
        private String adjustResult;
    }
}
