package com.county.vision.web;

import com.county.vision.common.ApiResponse;
import com.county.vision.domain.entity.AfterSales;
import com.county.vision.domain.entity.ComfortFollowup;
import com.county.vision.domain.entity.SubsidyVerification;
import com.county.vision.dto.VerificationDtos;
import com.county.vision.service.QueryService;
import com.county.vision.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 合作门店配镜补助发放核验、售后差额结算、配镜不适调整与适应回访接口。
 * 角色呈现以业务可见性为主：门店（CLINIC/HOSPITAL）上传材料与调整结果，卫健部门（GOV_HEALTH）复核/发放/结清。
 */
@Tag(name = "配镜补助发放核验（门店材料上传/逐项核验挂起/发放/售后差额/不适回访）")
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;
    private final QueryService queryService;

    public VerificationController(VerificationService verificationService, QueryService queryService) {
        this.verificationService = verificationService;
        this.queryService = queryService;
    }

    @Operation(summary = "合作门店上传处方/镜片参数/付款凭证/取镜签收并提交发放核验（不符自动挂起；传 glassesId 为补材料重提）")
    @PostMapping("/store-glasses")
    public ApiResponse<Long> storeGlasses(@RequestBody VerificationDtos.StoreGlassesRequest req) {
        return ApiResponse.ok(verificationService.submitStoreGlasses(req).getId());
    }

    @Operation(summary = "卫健部门复核挂起核验单（RELEASE 放行 / REJECT 驳回）")
    @PostMapping("/review")
    public ApiResponse<Long> review(@RequestBody VerificationDtos.VerificationReviewRequest req) {
        return ApiResponse.ok(verificationService.reviewVerification(req).getId());
    }

    @Operation(summary = "核验通过后发放补助（与补助申请一一对应，系统二次防重，杜绝发两次）")
    @PostMapping("/{id}/pay")
    public ApiResponse<Long> pay(@PathVariable Long id) {
        return ApiResponse.ok(verificationService.payVerification(id).getId());
    }

    @Operation(summary = "核验单台账（PENDING/PASSED/SUSPENDED/PAID/AFTER_SALES/CLOSED/REJECTED/ALL）")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.verifications(status));
    }

    @Operation(summary = "核验单详情（含七项逐项核验结论与比对明细）")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.verificationDetail(id));
    }

    @Operation(summary = "按配镜记录查核验单（前端补材料时回显）")
    @GetMapping("/by-glasses/{glassesId}")
    public ApiResponse<Map<String, Object>> byGlasses(@PathVariable Long glassesId) {
        return ApiResponse.ok(queryService.verificationByGlasses(glassesId));
    }

    @Operation(summary = "登记售后（退镜 REFUND/换片 LENS_REPLACE/仅换架 FRAME_REPLACE），按实际退款与新费用重算追回/补发差额")
    @PostMapping("/after-sales")
    public ApiResponse<Long> afterSales(@RequestBody VerificationDtos.AfterSalesRequest req) {
        return ApiResponse.ok(verificationService.registerAfterSales(req).getId());
    }

    @Operation(summary = "售后差额实际追回/补发结清，原补助与售后连档关闭")
    @PostMapping("/after-sales/settle")
    public ApiResponse<Long> settleAfterSales(@RequestBody VerificationDtos.AfterSalesSettleRequest req) {
        return ApiResponse.ok(verificationService.settleAfterSales(req).getId());
    }

    @Operation(summary = "售后处理台账（REQUESTED 待结清/SETTLED 已结清/ALL）")
    @GetMapping("/after-sales/list")
    public ApiResponse<List<Map<String, Object>>> afterSalesList(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.afterSalesList(status));
    }

    @Operation(summary = "学生反馈配镜不适（DIZZY 头晕/BLURRY 看不清/FRAME_FIT 镜架不合适/OTHER）")
    @PostMapping("/comfort/feedback")
    public ApiResponse<Long> feedback(@RequestBody VerificationDtos.DiscomfortFeedbackRequest req) {
        return ApiResponse.ok(verificationService.feedbackDiscomfort(req).getId());
    }

    @Operation(summary = "门店上传不适调整结果（可同时建立复查提醒）")
    @PostMapping("/comfort/adjust")
    public ApiResponse<Long> adjust(@RequestBody VerificationDtos.AdjustResultRequest req) {
        return ApiResponse.ok(verificationService.submitAdjustResult(req).getId());
    }

    @Operation(summary = "登记戴镜适应回访结果（已适应则闭环不适/回访事项）")
    @PostMapping("/comfort/callback")
    public ApiResponse<Long> callback(@RequestBody VerificationDtos.CallbackRequest req) {
        return ApiResponse.ok(verificationService.submitCallback(req).getId());
    }

    @Operation(summary = "不适调整与适应回访台账（PLANNED 待回访/DONE 已回访/ALL）")
    @GetMapping("/comfort/list")
    public ApiResponse<List<Map<String, Object>>> comfortList(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.comfortList(status));
    }
}
