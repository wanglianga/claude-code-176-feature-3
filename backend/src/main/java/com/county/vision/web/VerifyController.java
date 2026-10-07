package com.county.vision.web;

import com.county.vision.common.ApiResponse;
import com.county.vision.domain.entity.AfterSales;
import com.county.vision.domain.entity.StoreDispense;
import com.county.vision.domain.entity.SubsidyApplication;
import com.county.vision.dto.VerifyDtos;
import com.county.vision.service.DispenseVerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 合作门店配镜补助的发放核验与售后：
 * 门店上传材料 → 平台逐项核验（挂起/通过）→ 卫健确认发放（防二次发放）→
 * 退镜/换片/换架差额重算与结算 → 不适反馈调整与适应回访。
 */
@Tag(name = "门店配镜补助发放核验与售后")
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final DispenseVerifyService service;

    public VerifyController(DispenseVerifyService service) {
        this.service = service;
    }

    @Operation(summary = "门店上传配镜核验材料（处方/镜片参数/付款凭证/取镜签收），平台自动逐项核验")
    @PostMapping("/dispense")
    public ApiResponse<Long> submit(@RequestBody VerifyDtos.DispenseSubmitRequest req) {
        StoreDispense d = service.submit(req);
        return ApiResponse.ok(d.getId());
    }

    @Operation(summary = "挂起后门店更正材料重新提交核验")
    @PostMapping("/dispense/{id}/resubmit")
    public ApiResponse<Long> resubmit(@PathVariable Long id, @RequestBody VerifyDtos.DispenseSubmitRequest req) {
        return ApiResponse.ok(service.resubmit(id, req).getId());
    }

    @Operation(summary = "核验单台账（PENDING/PASSED/SUSPENDED/ALL）")
    @GetMapping("/dispenses")
    public ApiResponse<List<Map<String, Object>>> dispenses(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.dispenses(status));
    }

    @Operation(summary = "卫健确认发放补助（仅核验通过；已发放的配镜记录禁止重复发放）")
    @PostMapping("/dispense/{id}/pay")
    public ApiResponse<Long> pay(@PathVariable Long id) {
        SubsidyApplication app = service.pay(id);
        return ApiResponse.ok(app.getId());
    }

    @Operation(summary = "售后登记：退镜/换片/换架，按实际退款与新费用重算应追回或补发差额")
    @PostMapping("/after-sales")
    public ApiResponse<Long> registerAfterSales(@RequestBody VerifyDtos.AfterSalesRequest req) {
        AfterSales as = service.registerAfterSales(req);
        return ApiResponse.ok(as.getId());
    }

    @Operation(summary = "售后台账（PENDING_SETTLE/SETTLED/WAIT_ADJUST/ADJUSTED/ALL）")
    @GetMapping("/after-sales")
    public ApiResponse<List<Map<String, Object>>> afterSales(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.afterSalesList(status));
    }

    @Operation(summary = "卫健确认售后差额结算（追回或补发，并更新原补助）")
    @PostMapping("/after-sales/{id}/settle")
    public ApiResponse<Long> settle(@PathVariable Long id) {
        return ApiResponse.ok(service.settle(id).getId());
    }

    @Operation(summary = "学生不适反馈登记（头晕/看不清/镜架不合适），通知门店调整")
    @PostMapping("/after-sales/feedback")
    public ApiResponse<Long> feedback(@RequestBody VerifyDtos.FeedbackRequest req) {
        return ApiResponse.ok(service.feedback(req).getId());
    }

    @Operation(summary = "门店上传调整结果：保留售后记录并继续提醒复查与适应回访")
    @PostMapping("/after-sales/{id}/adjust")
    public ApiResponse<Long> adjust(@PathVariable Long id, @RequestBody VerifyDtos.AdjustResultRequest req) {
        req.setAfterSalesId(id);
        return ApiResponse.ok(service.uploadAdjustResult(id, req.getAdjustResult()).getId());
    }
}
