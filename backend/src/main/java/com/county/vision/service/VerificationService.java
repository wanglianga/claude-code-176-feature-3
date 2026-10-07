package com.county.vision.service;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.*;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.VerificationDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 合作门店配镜补助「发放核验」与售后差额结算：
 *
 * 1. 门店在学生取镜后上传验光处方、镜片加工参数、付款凭证、取镜签收四类材料；
 * 2. 平台与最近一次复诊处方、家庭补助资格、县域补助标准逐项比对，确认同一学生、同一副眼镜：
 *    处方不一致 / 参数缺失 / 付款凭证重复 / 门店重复提交 → 一律挂起，不能直接发放补助；
 * 3. 挂起单由卫健部门人工复核：放行（核定应发金额）或驳回；
 * 4. 发放与补助申请一一对应，全局防重（同学生在途/已发放单、同一付款凭证）；
 * 5. 发放后家长退镜、换片或只换镜架：按门店实际退款与新费用重算补助，
 *    应追回/应补发差额始终与原补助连在一起，结清后售后闭环；
 * 6. 学生反馈头晕、看不清、镜架不合适：门店上传调整结果，平台保留记录并继续提醒复查与适应回访。
 */
@Service
public class VerificationService {

    private final StudentRepository studentRepo;
    private final GlassesRecordRepository glassesRepo;
    private final VisitRecordRepository visitRepo;
    private final ParentConfirmationRepository confirmRepo;
    private final SubsidyApplicationRepository subsidyRepo;
    private final SubsidyVerificationRepository verificationRepo;
    private final AfterSalesRepository afterSalesRepo;
    private final ComfortFollowupRepository comfortRepo;
    private final ReviewFollowUpRepository followupRepo;
    private final IssueTrackerRepository issueRepo;
    private final TimelineService timeline;

    /** 县域配镜补助标准上限（元）：单副眼镜补助不超过该金额 */
    @Value("${vision.subsidy.cap:300}")
    private BigDecimal subsidyCap;

    public VerificationService(StudentRepository studentRepo, GlassesRecordRepository glassesRepo,
                               VisitRecordRepository visitRepo, ParentConfirmationRepository confirmRepo,
                               SubsidyApplicationRepository subsidyRepo,
                               SubsidyVerificationRepository verificationRepo,
                               AfterSalesRepository afterSalesRepo, ComfortFollowupRepository comfortRepo,
                               ReviewFollowUpRepository followupRepo, IssueTrackerRepository issueRepo,
                               TimelineService timeline) {
        this.studentRepo = studentRepo;
        this.glassesRepo = glassesRepo;
        this.visitRepo = visitRepo;
        this.confirmRepo = confirmRepo;
        this.subsidyRepo = subsidyRepo;
        this.verificationRepo = verificationRepo;
        this.afterSalesRepo = afterSalesRepo;
        this.comfortRepo = comfortRepo;
        this.followupRepo = followupRepo;
        this.issueRepo = issueRepo;
        this.timeline = timeline;
    }

    /* ================= 1. 门店上传四类材料并提交发放核验 ================= */

    @Transactional
    public SubsidyVerification submitStoreGlasses(VerificationDtos.StoreGlassesRequest req) {
        GlassesRecord g;
        SubsidyVerification v;
        boolean resubmit;
        if (req.getGlassesId() != null) {
            g = glassesRepo.findById(req.getGlassesId())
                    .orElseThrow(() -> new BusinessException("配镜记录不存在"));
            // 补材料重提时学生身份以原配镜记录为准（studentId 可省略）
            if (req.getStudentId() == null) req.setStudentId(g.getStudent().getId());
        } else {
            g = null;
        }
        Student s = studentRepo.findById(req.getStudentId())
                .orElseThrow(() -> new BusinessException("学生不存在"));

        if (req.getGlassesId() != null) {
            if (!g.getStudent().getId().equals(s.getId())) {
                throw new BusinessException("配镜记录与学生不匹配");
            }
            v = verificationRepo.findByGlassesId(g.getId()).orElse(null);
            if (v == null) {
                throw new BusinessException("该配镜记录尚无核验单，不能按补材料方式提交");
            }
            if (v.getStatus() != VerificationStatus.SUSPENDED && v.getStatus() != VerificationStatus.REJECTED) {
                throw new BusinessException("仅挂起或已驳回的核验单允许补材料后重新提交");
            }
            resubmit = true;
        } else {
            g = new GlassesRecord();
            g.setStudent(s);
            g.setPartnerOptician(true);
            g.setDiscomfort(false);
            g.setDiscomfortResolved(false);
            v = new SubsidyVerification();
            v.setStudent(s);
            v.setGlasses(g);
            resubmit = false;
        }

        // 写入门店上传材料
        g.setOpticianOrg(blankToNull(req.getOpticianOrg()));
        g.setGlassesDate(req.getGlassesDate() == null ? LocalDate.now() : req.getGlassesDate());
        g.setAmount(req.getAmount());
        g.setLensBrand(blankToNull(req.getLensBrand()));
        g.setLensInfo(buildLensSummary(req));

        g.setRxNo(blankToNull(req.getRxNo()));
        g.setRxSphereLeft(req.getRxSphereLeft());
        g.setRxSphereRight(req.getRxSphereRight());
        g.setRxCylinderLeft(req.getRxCylinderLeft());
        g.setRxCylinderRight(req.getRxCylinderRight());
        g.setRxAxisLeft(req.getRxAxisLeft());
        g.setRxAxisRight(req.getRxAxisRight());
        g.setRxPd(req.getRxPd());

        g.setLensSphereLeft(req.getLensSphereLeft());
        g.setLensSphereRight(req.getLensSphereRight());
        g.setLensCylinderLeft(req.getLensCylinderLeft());
        g.setLensCylinderRight(req.getLensCylinderRight());
        g.setLensAxisLeft(req.getLensAxisLeft());
        g.setLensAxisRight(req.getLensAxisRight());
        g.setLensPd(req.getLensPd());

        g.setPaymentVoucherNo(blankToNull(req.getPaymentVoucherNo()));
        g.setPaymentVoucherFile(blankToNull(req.getPaymentVoucherFile()));
        g.setPickedUp(Boolean.TRUE.equals(req.getPickedUp()));
        g.setPickupDate(req.getPickupDate());
        g.setReceiverName(blankToNull(req.getReceiverName()));
        g.setPickupRemark(blankToNull(req.getPickupRemark()));
        glassesRepo.save(g);

        if (!resubmit) {
            timeline.event(s, EventType.GLASSES,
                    "合作门店配镜完成并取镜：" + nz(g.getOpticianOrg()) + "，金额 " + money(g.getAmount())
                            + "，" + nz(g.getLensBrand()) + "；门店已上传处方、镜片参数、付款凭证、取镜签收四类材料");
            timeline.changeStage(s, StudentStage.MANAGING);
        }

        // 逐项核验
        List<String> holds = new ArrayList<>();
        List<String> details = new ArrayList<>();

        VisitRecord latestVisit = visitRepo.findByStudentIdOrderByVisitDateAscIdAsc(s.getId())
                .stream().reduce((a, b) -> b).orElse(null);

        runStudentCheck(s, g, v, holds, details);
        runPrescriptionCheck(latestVisit, g, v, holds, details);
        runLensParamCheck(latestVisit, g, v, holds, details);
        runEligibilityCheck(s, latestVisit, v, holds, details);
        runVoucherCheck(g, v, holds, details);
        runPickupCheck(g, v, holds, details);
        runStandardAndDuplicateCheck(s, g, v, resubmit, holds, details);

        v.setStandardAmount(subsidyCap);
        v.setCheckDetail(String.join("\n", details));

        if (holds.isEmpty()) {
            BigDecimal payable = g.getAmount() == null ? subsidyCap : g.getAmount().min(subsidyCap);
            v.setPayableAmount(payable);
            v.setStatus(VerificationStatus.PASSED);
            v.setHoldReasons(null);
            timeline.event(s, EventType.VERIFICATION,
                    "门店提交发放核验（机构：" + nz(g.getOpticianOrg()) + "，凭证号：" + nz(g.getPaymentVoucherNo())
                            + "）：同一学生、同一副眼镜，七项逐项核验通过，按补助标准上限 "
                            + money(subsidyCap) + " 核定应发 " + money(payable));
            closeHoldIssue(s, "核验通过，材料与最近复诊处方、家庭资格、补助标准逐项一致");
        } else {
            v.setPayableAmount(null);
            v.setStatus(VerificationStatus.SUSPENDED);
            v.setHoldReasons(String.join("；", holds));
            timeline.raiseIssue(s, ScreenFlag.VERIFICATION_HOLD,
                    "合作门店配镜补助核验挂起：" + String.join("；", holds));
            timeline.event(s, EventType.VERIFICATION,
                    "门店提交发放核验（机构：" + nz(g.getOpticianOrg()) + "）未通过自动核验，已挂起："
                            + String.join("；", holds) + "。挂起期间不得发放补助，待卫健部门复核。");
        }
        v.setSubmitter(com.county.vision.security.SecurityUtils.actorLabel());
        v.setSubmitTime(java.time.LocalDateTime.now());
        return verificationRepo.save(v);
    }

    /* ---------- 七个核验项 ---------- */

    /** 同一学生：签收人须与登记家长一致 */
    private void runStudentCheck(Student s, GlassesRecord g, SubsidyVerification v,
                                 List<String> holds, List<String> details) {
        String receiver = norm(g.getReceiverName());
        String parent = norm(s.getParentName());
        if (receiver.isEmpty()) {
            v.setStudentCheck(CheckResult.MISSING);
            holds.add("取镜签收人缺失，无法确认是同一学生");
            details.add("①同一学生：缺失——未填写签收人（登记家长：" + nz(s.getParentName()) + "）");
        } else if (!parent.isEmpty() && !receiver.equals(parent)) {
            v.setStudentCheck(CheckResult.FAIL);
            holds.add("签收人「" + g.getReceiverName() + "」与登记家长「" + s.getParentName() + "」不一致");
            details.add("①同一学生：不符——签收人 " + g.getReceiverName() + "，登记家长 " + s.getParentName());
        } else {
            v.setStudentCheck(CheckResult.PASS);
            details.add("①同一学生：通过——学籍 " + nz(s.getStudentNo()) + "，签收人 " + g.getReceiverName());
        }
    }

    /** 同一副眼镜（一）：门店上传处方与最近一次复诊处方逐项一致 */
    private void runPrescriptionCheck(VisitRecord latest, GlassesRecord g, SubsidyVerification v,
                                      List<String> holds, List<String> details) {
        List<String> line = new ArrayList<>();
        if (latest == null) {
            v.setPrescriptionCheck(CheckResult.FAIL);
            holds.add("缺少最近一次复诊处方，无法逐项对应");
            details.add("②处方一致性：不符——该学生无复诊记录");
            return;
        }
        boolean anyMissing = false;
        boolean anyMismatch = false;
        int r;
        r = compareOne("球镜(左)", g.getRxSphereLeft(), latest.getRxSphereLeft(), line); anyMissing |= r == 1; anyMismatch |= r == 2;
        r = compareOne("球镜(右)", g.getRxSphereRight(), latest.getRxSphereRight(), line); anyMissing |= r == 1; anyMismatch |= r == 2;
        r = compareOne("柱镜(左)", g.getRxCylinderLeft(), latest.getRxCylinderLeft(), line); anyMissing |= r == 1; anyMismatch |= r == 2;
        r = compareOne("柱镜(右)", g.getRxCylinderRight(), latest.getRxCylinderRight(), line); anyMissing |= r == 1; anyMismatch |= r == 2;
        r = compareOne("轴位(左)", g.getRxAxisLeft(), latest.getRxAxisLeft(), line); anyMissing |= r == 1; anyMismatch |= r == 2;
        r = compareOne("轴位(右)", g.getRxAxisRight(), latest.getRxAxisRight(), line); anyMissing |= r == 1; anyMismatch |= r == 2;
        r = compareOne("瞳距", g.getRxPd(), latest.getPd(), line); anyMissing |= r == 1; anyMismatch |= r == 2;

        String head = "②处方一致性（对照 " + latest.getVisitDate() + " " + nz(latest.getOrgName())
                + " 最近一次复诊处方，处方号 " + nz(g.getRxNo()) + "）：";
        if (anyMissing) {
            v.setPrescriptionCheck(CheckResult.MISSING);
            holds.add("门店上传处方参数缺失或与复诊处方缺项对应不上");
            details.add(head + "缺失——" + String.join("；", line));
        } else if (anyMismatch) {
            v.setPrescriptionCheck(CheckResult.FAIL);
            holds.add("门店上传处方与最近一次复诊处方不一致：" + mismatchItems(line));
            details.add(head + "不符——" + String.join("；", line));
        } else {
            v.setPrescriptionCheck(CheckResult.PASS);
            details.add(head + "通过——球镜/柱镜/轴位/瞳距逐项一致");
        }
    }

    /** 同一副眼镜（二）：镜片实际加工参数完整且按该处方加工 */
    private void runLensParamCheck(VisitRecord latest, GlassesRecord g, SubsidyVerification v,
                                   List<String> holds, List<String> details) {
        List<String> required = new ArrayList<>();
        if (isBlank(g.getLensSphereLeft())) required.add("镜片球镜(左)");
        if (isBlank(g.getLensSphereRight())) required.add("镜片球镜(右)");
        if (isBlank(g.getLensPd())) required.add("加工瞳距");
        // 处方有散光时，柱镜/轴位同样必填
        if (latest != null) {
            if (!isBlank(latest.getRxCylinderLeft()) && isBlank(g.getLensCylinderLeft())) required.add("镜片柱镜(左)");
            if (!isBlank(latest.getRxCylinderRight()) && isBlank(g.getLensCylinderRight())) required.add("镜片柱镜(右)");
            if (!isBlank(latest.getRxAxisLeft()) && isBlank(g.getLensAxisLeft())) required.add("镜片轴位(左)");
            if (!isBlank(latest.getRxAxisRight()) && isBlank(g.getLensAxisRight())) required.add("镜片轴位(右)");
        }
        if (!required.isEmpty()) {
            v.setLensParamCheck(CheckResult.MISSING);
            holds.add("镜片加工参数缺失：" + String.join("、", required));
            details.add("③镜片参数：缺失——" + String.join("、", required)
                    + "（品类：" + nz(g.getLensBrand()) + "）");
            return;
        }
        List<String> line = new ArrayList<>();
        boolean mismatch = false;
        int r;
        r = compareOne("球镜(左)", g.getLensSphereLeft(), g.getRxSphereLeft(), line); mismatch |= r == 2;
        r = compareOne("球镜(右)", g.getLensSphereRight(), g.getRxSphereRight(), line); mismatch |= r == 2;
        r = compareOne("柱镜(左)", g.getLensCylinderLeft(), g.getRxCylinderLeft(), line); mismatch |= r == 2;
        r = compareOne("柱镜(右)", g.getLensCylinderRight(), g.getRxCylinderRight(), line); mismatch |= r == 2;
        r = compareOne("轴位(左)", g.getLensAxisLeft(), g.getRxAxisLeft(), line); mismatch |= r == 2;
        r = compareOne("轴位(右)", g.getLensAxisRight(), g.getRxAxisRight(), line); mismatch |= r == 2;
        r = compareOne("瞳距", g.getLensPd(), g.getRxPd(), line); mismatch |= r == 2;
        if (mismatch) {
            v.setLensParamCheck(CheckResult.FAIL);
            holds.add("镜片实际加工参数与处方不一致：" + mismatchItems(line));
            details.add("③镜片参数：不符——" + String.join("；", line));
        } else {
            v.setLensParamCheck(CheckResult.PASS);
            details.add("③镜片参数：通过——" + nz(g.getLensBrand()) + "，按处方加工，参数完整一致");
        }
    }

    /** 家庭补助资格：家长已确认处置 + 复诊医生初判符合补助 */
    private void runEligibilityCheck(Student s, VisitRecord latest, SubsidyVerification v,
                                     List<String> holds, List<String> details) {
        boolean parentConfirmed = confirmRepo.findFirstByStudentIdOrderByIdDesc(s.getId())
                .map(ParentConfirmation::getConfirmed).orElse(false);
        boolean doctorEligible = latest != null && Boolean.TRUE.equals(latest.getSubsidyEligible());
        if (!parentConfirmed && !doctorEligible) {
            v.setEligibilityCheck(CheckResult.FAIL);
            holds.add("家庭不符合补助资格：家长未确认且复诊医生初判不符合");
            details.add("④家庭资格：不符——家长未确认处置，医生初判不符合");
        } else if (!parentConfirmed) {
            v.setEligibilityCheck(CheckResult.FAIL);
            holds.add("家庭资格待补：家长尚未完成告知确认");
            details.add("④家庭资格：不符——缺少家长确认");
        } else if (!doctorEligible) {
            v.setEligibilityCheck(CheckResult.FAIL);
            holds.add("复诊医生初判不符合配镜补助，家庭不在补助范围");
            details.add("④家庭资格：不符——最近复诊医生初判不符合补助");
        } else {
            v.setEligibilityCheck(CheckResult.PASS);
            details.add("④家庭资格：通过——家长已确认，" + nz(latest.getOrgName()) + "医生初判符合补助");
        }
    }

    /** 付款凭证：必填且不得与其他眼镜/核验单共用同一凭证号 */
    private void runVoucherCheck(GlassesRecord g, SubsidyVerification v,
                                 List<String> holds, List<String> details) {
        String no = norm(g.getPaymentVoucherNo());
        if (no.isEmpty()) {
            v.setVoucherCheck(CheckResult.MISSING);
            holds.add("付款凭证缺失（发票/收据号未填）");
            details.add("⑤付款凭证：缺失——未填凭证号");
            return;
        }
        GlassesRecord other = glassesRepo.findFirstByPaymentVoucherNoAndIdNot(no, g.getId()).orElse(null);
        if (other != null) {
            v.setVoucherCheck(CheckResult.FAIL);
            holds.add("付款凭证重复：凭证号 " + g.getPaymentVoucherNo() + " 已用于学生「"
                    + other.getStudent().getName() + "」的配镜（配镜记录#" + other.getId() + "）");
            details.add("⑤付款凭证：不符——凭证号 " + g.getPaymentVoucherNo()
                    + " 已被配镜记录#" + other.getId() + " 使用，不能重复领取补助");
        } else {
            v.setVoucherCheck(CheckResult.PASS);
            details.add("⑤付款凭证：通过——凭证号 " + g.getPaymentVoucherNo() + " 全局唯一");
        }
    }

    /** 取镜签收：已取镜、签收日期、签收人齐全 */
    private void runPickupCheck(GlassesRecord g, SubsidyVerification v,
                                List<String> holds, List<String> details) {
        if (!Boolean.TRUE.equals(g.getPickedUp()) || g.getPickupDate() == null || isBlank(g.getReceiverName())) {
            v.setPickupCheck(CheckResult.MISSING);
            List<String> miss = new ArrayList<>();
            if (!Boolean.TRUE.equals(g.getPickedUp())) miss.add("未确认取镜");
            if (g.getPickupDate() == null) miss.add("缺签收日期");
            if (isBlank(g.getReceiverName())) miss.add("缺签收人");
            holds.add("取镜签收材料不完整：" + String.join("、", miss));
            details.add("⑥取镜签收：缺失——" + String.join("、", miss));
        } else {
            v.setPickupCheck(CheckResult.PASS);
            details.add("⑥取镜签收：通过——" + g.getPickupDate() + " 由 " + g.getReceiverName() + " 签收");
        }
    }

    /** 补助标准 + 防重复：金额合规，且同一学生不能有在途单或已发放补助（一副眼镜只补一次） */
    private void runStandardAndDuplicateCheck(Student s, GlassesRecord g, SubsidyVerification self,
                                              boolean resubmit, List<String> holds, List<String> details) {
        boolean standardFail = false;
        if (g.getAmount() == null || g.getAmount().signum() <= 0) {
            holds.add("配镜费用缺失或不合法，无法套用补助标准");
            details.add("⑦补助标准：缺失——未填写配镜金额");
            standardFail = true;
        }

        List<String> dupHits = new ArrayList<>();
        for (SubsidyVerification other : verificationRepo.findByStudentIdOrderByIdDesc(s.getId())) {
            if (other.getId() != null && other.getId().equals(self.getId())) continue;
            switch (other.getStatus()) {
                case PENDING, PASSED, SUSPENDED ->
                        dupHits.add("已有在途核验单#" + other.getId() + "（" + other.getStatus().getLabel() + "）");
                case PAID, AFTER_SALES ->
                        dupHits.add("核验单#" + other.getId() + "补助已发放/售后挂接中（"
                                + other.getStatus().getLabel() + "），不得二次发放");
                default -> { }
            }
        }
        if (!dupHits.isEmpty()) {
            holds.add("门店重复提交：" + String.join("；", dupHits));
            details.add("⑦补助标准：不符——" + String.join("；", dupHits));
            self.setStandardCheck(CheckResult.FAIL);
            return;
        }
        if (standardFail) {
            self.setStandardCheck(CheckResult.MISSING);
        } else {
            self.setStandardCheck(CheckResult.PASS);
            BigDecimal payable = g.getAmount().min(subsidyCap);
            details.add("⑦补助标准：通过——配镜费用 " + money(g.getAmount()) + "，按县域标准上限 "
                    + money(subsidyCap) + " 核定应发 " + money(payable)
                    + (resubmit ? "（补材料重新提交）" : ""));
        }
    }

    /* ================= 2. 卫健部门对挂起单人工复核 ================= */

    @Transactional
    public SubsidyVerification reviewVerification(VerificationDtos.VerificationReviewRequest req) {
        SubsidyVerification v = verificationRepo.findById(req.getVerificationId())
                .orElseThrow(() -> new BusinessException("核验单不存在"));
        if (v.getStatus() != VerificationStatus.SUSPENDED) {
            throw new BusinessException("仅挂起中的核验单需要人工复核");
        }
        Student s = v.getStudent();
        switch (req.getAction()) {
            case "RELEASE" -> {
                v.setStatus(VerificationStatus.PASSED);
                BigDecimal payable = req.getPayableAmount() != null ? req.getPayableAmount() : v.getPayableAmount();
                if (payable == null) {
                    payable = v.getGlasses().getAmount() == null ? subsidyCap
                            : v.getGlasses().getAmount().min(subsidyCap);
                }
                if (payable.signum() < 0 || payable.compareTo(subsidyCap) > 0) {
                    throw new BusinessException("核定金额须在 0 ~ " + subsidyCap + " 元之间");
                }
                v.setPayableAmount(payable);
                v.setOpinion(req.getOpinion());
                timeline.event(s, EventType.VERIFICATION,
                        "卫健部门人工复核放行核验单#" + v.getId() + "，核定应发 " + money(payable)
                                + "：" + nz(req.getOpinion()));
                closeHoldIssue(s, "卫健复核放行：" + nz(req.getOpinion()));
            }
            case "REJECT" -> {
                v.setStatus(VerificationStatus.REJECTED);
                v.setPayableAmount(null);
                v.setOpinion(req.getOpinion());
                timeline.event(s, EventType.VERIFICATION,
                        "卫健部门复核驳回核验单#" + v.getId() + "，不予发放补助：" + nz(req.getOpinion()));
                closeHoldIssue(s, "核验驳回：" + nz(req.getOpinion()));
            }
            default -> throw new BusinessException("复核动作只能为 RELEASE / REJECT");
        }
        v.setReviewer(com.county.vision.security.SecurityUtils.actorLabel());
        v.setReviewTime(java.time.LocalDateTime.now());
        return verificationRepo.save(v);
    }

    /* ================= 3. 发放补助（与补助申请一一对应，严格防重） ================= */

    @Transactional
    public SubsidyVerification payVerification(Long verificationId) {
        SubsidyVerification v = verificationRepo.findById(verificationId)
                .orElseThrow(() -> new BusinessException("核验单不存在"));
        if (v.getStatus() != VerificationStatus.PASSED) {
            throw new BusinessException("仅核验通过的单据可以发放补助（当前状态：" + v.getStatus().getLabel() + "）");
        }
        Student s = v.getStudent();
        // 最后一道防线：该学生任何其他单据已发放，直接拒绝
        boolean alreadyPaid = verificationRepo.findByStudentIdOrderByIdDesc(s.getId()).stream()
                .anyMatch(o -> !o.getId().equals(v.getId())
                        && (o.getStatus() == VerificationStatus.PAID
                        || o.getStatus() == VerificationStatus.AFTER_SALES));
        if (alreadyPaid) {
            throw new BusinessException("该学生已有发放完成且未结清的补助，系统拒绝二次发放");
        }
        BigDecimal payable = v.getPayableAmount();
        if (payable == null) {
            payable = v.getGlasses().getAmount() == null ? subsidyCap : v.getGlasses().getAmount().min(subsidyCap);
        }

        SubsidyApplication app = v.getSubsidy();
        if (app == null) {
            app = new SubsidyApplication();
            app.setStudent(s);
            app.setGlasses(v.getGlasses());
            app.setApplyAmount(v.getGlasses().getAmount());
            app.setApplicant(v.getSubmitter());
            app.setApplyTime(v.getSubmitTime());
        }
        app.setApprovedAmount(payable);
        app.setStatus(SubsidyStatus.PAID);
        app.setApprover(com.county.vision.security.SecurityUtils.actorLabel());
        app.setApproveTime(java.time.LocalDateTime.now());
        app.setOpinion("核验单#" + v.getId() + " 七项核验通过后发放（同一学生、同一副眼镜，凭证唯一）");
        subsidyRepo.save(app);

        v.setSubsidy(app);
        v.setStatus(VerificationStatus.PAID);
        v.setPaidAmount(payable);
        v.setPaidTime(java.time.LocalDateTime.now());
        verificationRepo.save(v);

        closeHoldIssue(s, "核验通过，补助已发放 " + money(payable));
        timeline.event(s, EventType.SUBSIDY,
                "合作门店配镜补助已发放 " + money(payable) + "（核验单#" + v.getId()
                        + "，付款凭证 " + nz(v.getGlasses().getPaymentVoucherNo()) + "），原申请/核准/发放与该副眼镜绑定");
        return v;
    }

    /* ================= 4. 售后：退镜/换片/换架 → 重算追回或补发差额 ================= */

    @Transactional
    public AfterSales registerAfterSales(VerificationDtos.AfterSalesRequest req) {
        GlassesRecord g = glassesRepo.findById(req.getGlassesId())
                .orElseThrow(() -> new BusinessException("配镜记录不存在"));
        SubsidyVerification v = verificationRepo.findByGlassesId(g.getId())
                .orElseThrow(() -> new BusinessException("该配镜记录没有发放核验单"));
        if (v.getStatus() != VerificationStatus.PAID && v.getStatus() != VerificationStatus.AFTER_SALES) {
            throw new BusinessException("补助尚未发放（核验单状态：" + v.getStatus().getLabel()
                    + "），暂不涉及补助差额结算");
        }
        AfterSalesType type;
        try {
            type = AfterSalesType.valueOf(req.getType());
        } catch (Exception e) {
            throw new BusinessException("售后类型只能为 REFUND 退镜 / LENS_REPLACE 换片 / FRAME_REPLACE 仅换架");
        }
        BigDecimal refund = req.getRefundAmount() == null ? BigDecimal.ZERO : req.getRefundAmount();
        BigDecimal newCost = req.getNewCostAmount() == null ? BigDecimal.ZERO : req.getNewCostAmount();
        if (refund.signum() < 0 || newCost.signum() < 0) {
            throw new BusinessException("退款金额与新费用不能为负");
        }
        BigDecimal originalCost = g.getAmount() == null ? BigDecimal.ZERO : g.getAmount();
        BigDecimal originalSubsidy = v.getPaidAmount() == null ? BigDecimal.ZERO : v.getPaidAmount();

        // 售后自付费用基数 = 原费用 - 门店实际退款 + 新发生费用
        BigDecimal settledCost = originalCost.subtract(refund).add(newCost).max(BigDecimal.ZERO);
        BigDecimal recalc = settledCost.min(subsidyCap);
        BigDecimal diff = originalSubsidy.subtract(recalc);

        AfterSales a = new AfterSales();
        a.setStudent(g.getStudent());
        a.setGlasses(g);
        a.setVerification(v);
        a.setSubsidy(v.getSubsidy());
        a.setType(type);
        a.setApplyDate(req.getApplyDate() == null ? LocalDate.now() : req.getApplyDate());
        a.setProcessedDate(req.getProcessedDate());
        a.setRefundAmount(refund);
        a.setNewCostAmount(newCost);
        a.setSettledCostAmount(settledCost);
        a.setOriginalSubsidy(originalSubsidy);
        a.setRecalculatedSubsidy(recalc);
        a.setHandler(com.county.vision.security.SecurityUtils.actorLabel());
        a.setRemark(req.getRemark());

        String moneyNote;
        if (diff.signum() > 0) {
            a.setDirection(SettleDirection.RECOVER);
            a.setSettleAmount(diff);
            moneyNote = "原补助 " + money(originalSubsidy) + "，按实际退款/新费用重算应补助 "
                    + money(recalc) + "，应向家长追回差额 " + money(diff);
        } else if (diff.signum() < 0) {
            a.setDirection(SettleDirection.SUPPLEMENT);
            a.setSettleAmount(diff.negate());
            moneyNote = "原补助 " + money(originalSubsidy) + "，按实际退款/新费用重算应补助 "
                    + money(recalc) + "，应向家长补发差额 " + money(diff.negate());
        } else {
            a.setDirection(SettleDirection.NONE);
            a.setSettleAmount(BigDecimal.ZERO);
            moneyNote = "原补助 " + money(originalSubsidy) + "，重算应补助 " + money(recalc) + "，无差额";
        }

        boolean settledNow = Boolean.TRUE.equals(req.getSettledNow()) || a.getDirection() == SettleDirection.NONE;
        if (settledNow) {
            a.setStatus("SETTLED");
            a.setSettleVoucherNo(req.getSettleVoucherNo());
            a.setSettleDate(LocalDate.now());
            v.setStatus(VerificationStatus.CLOSED);
        } else {
            a.setStatus("REQUESTED");
            v.setStatus(VerificationStatus.AFTER_SALES);
            timeline.raiseIssue(g.getStudent(), ScreenFlag.AFTER_SALES_PENDING,
                    type.getLabel() + "售后差额待结清：" + moneyNote);
        }
        AfterSales saved = afterSalesRepo.save(a);
        verificationRepo.save(v);

        String typeNote = switch (type) {
            case REFUND -> "家长退镜，门店实际退款 " + money(refund);
            case LENS_REPLACE -> "家长只更换镜片，旧片折退 " + money(refund) + "，新镜片费用 " + money(newCost);
            case FRAME_REPLACE -> "家长只更换镜架，旧架折退 " + money(refund) + "，新镜架费用 " + money(newCost);
        };
        timeline.event(g.getStudent(), EventType.AFTER_SALES,
                "售后处理【" + type.getLabel() + "】（售后单#" + saved.getId() + "，关联核验单#" + v.getId()
                        + "、原补助 " + money(originalSubsidy) + "）：" + typeNote + "；售后费用基数 "
                        + money(settledCost) + "；" + moneyNote
                        + ("SETTLED".equals(a.getStatus()) ? "；差额已结清，售后闭环" : "；差额待结清，原补助继续挂账"));
        return saved;
    }

    /** 差额实际追回/补发到位，售后单与核验单一并闭环 */
    @Transactional
    public AfterSales settleAfterSales(VerificationDtos.AfterSalesSettleRequest req) {
        AfterSales a = afterSalesRepo.findById(req.getAfterSalesId())
                .orElseThrow(() -> new BusinessException("售后单不存在"));
        if ("SETTLED".equals(a.getStatus())) {
            throw new BusinessException("该售后单已结清");
        }
        a.setStatus("SETTLED");
        a.setSettleVoucherNo(req.getSettleVoucherNo());
        a.setSettleDate(req.getSettleDate() == null ? LocalDate.now() : req.getSettleDate());
        if (req.getRemark() != null) a.setRemark(req.getRemark());
        afterSalesRepo.save(a);

        SubsidyVerification v = a.getVerification();
        v.setStatus(VerificationStatus.CLOSED);
        verificationRepo.save(v);

        String note = a.getDirection().getLabel() + "差额 " + money(a.getSettleAmount()) + " 已结清"
                + (req.getSettleVoucherNo() == null ? "" : "（凭证 " + req.getSettleVoucherNo() + "）");
        closeIssue(a.getStudent(), ScreenFlag.AFTER_SALES_PENDING, note);
        timeline.event(a.getStudent(), EventType.AFTER_SALES,
                "售后单#" + a.getId() + " 差额结清：" + note + "，原补助 " + money(a.getOriginalSubsidy())
                        + " 与售后处理连档完成，核验单#" + v.getId() + " 关闭");
        return a;
    }

    /* ================= 5. 配镜不适：反馈 → 门店调整结果 → 适应回访 ================= */

    @Transactional
    public ComfortFollowup feedbackDiscomfort(VerificationDtos.DiscomfortFeedbackRequest req) {
        GlassesRecord g = glassesRepo.findById(req.getGlassesId())
                .orElseThrow(() -> new BusinessException("配镜记录不存在"));
        DiscomfortType type;
        try {
            type = DiscomfortType.valueOf(req.getDiscomfortType());
        } catch (Exception e) {
            throw new BusinessException("不适类型只能为 DIZZY 头晕 / BLURRY 看不清 / FRAME_FIT 镜架不合适 / OTHER");
        }
        LocalDate feedbackDate = req.getFeedbackDate() == null ? LocalDate.now() : req.getFeedbackDate();

        ComfortFollowup c = new ComfortFollowup();
        c.setStudent(g.getStudent());
        c.setGlasses(g);
        c.setDiscomfortType(type.name());
        c.setFeedbackDate(feedbackDate);
        c.setFeedback(req.getFeedback());
        // 取镜/调整后 7 天内做戴镜适应回访
        c.setCallbackDueDate(feedbackDate.plusDays(7));
        c.setCallbackStatus("PLANNED");
        c.setResolved(false);
        c.setHandler(com.county.vision.security.SecurityUtils.actorLabel());
        ComfortFollowup saved = comfortRepo.save(c);

        g.setDiscomfort(true);
        g.setDiscomfortType(type.name());
        g.setDiscomfortResolved(false);
        g.setDiscomfortDetail(type.getLabel() + "：" + nz(req.getFeedback()));
        glassesRepo.save(g);

        timeline.raiseIssue(g.getStudent(), ScreenFlag.GLASSES_DISCOMFORT,
                "学生反馈配镜不适（" + type.getLabel() + "）：" + nz(req.getFeedback()) + "，待门店上传调整结果");
        timeline.raiseIssue(g.getStudent(), ScreenFlag.ADAPT_FOLLOWUP,
                "配镜适应回访中：应在 " + c.getCallbackDueDate() + " 前完成适应回访；如症状持续须提醒复查");
        timeline.event(g.getStudent(), EventType.COMFORT,
                "学生反馈配镜不适【" + type.getLabel() + "】：" + nz(req.getFeedback())
                        + "；已建售后跟进记录，待门店上传调整结果，并计划 " + c.getCallbackDueDate() + " 适应回访");
        return saved;
    }

    /** 门店上传调整结果；必要时建立/更新复查提醒 */
    @Transactional
    public ComfortFollowup submitAdjustResult(VerificationDtos.AdjustResultRequest req) {
        ComfortFollowup c = comfortRepo.findById(req.getFollowupId())
                .orElseThrow(() -> new BusinessException("售后跟进记录不存在"));
        c.setAdjustResult(req.getAdjustResult());
        c.setAdjustDate(req.getAdjustDate() == null ? LocalDate.now() : req.getAdjustDate());
        c.setReviewAdvised(Boolean.TRUE.equals(req.getReviewAdvised()));
        c.setReviewPlannedDate(req.getReviewPlannedDate());
        comfortRepo.save(c);

        GlassesRecord g = c.getGlasses();
        g.setDiscomfortDetail(nz(g.getDiscomfortDetail()) + "；门店调整：" + nz(req.getAdjustResult()));
        glassesRepo.save(g);

        if (c.getReviewAdvised() && c.getReviewPlannedDate() != null) {
            ReviewFollowUp f = new ReviewFollowUp();
            f.setStudent(c.getStudent());
            f.setPlannedDate(c.getReviewPlannedDate());
            f.setStatus("PLANNED");
            f.setOrgName(g.getOpticianOrg());
            f.setRemark("配镜不适（" + DiscomfortType.valueOf(c.getDiscomfortType()).getLabel()
                    + "）门店调整后建议的复查提醒");
            followupRepo.save(f);
            timeline.event(c.getStudent(), EventType.REVIEW,
                    "门店建议返院复查，已建立复查提醒，应在 " + c.getReviewPlannedDate() + " 前完成");
        }
        timeline.event(c.getStudent(), EventType.COMFORT,
                "门店上传配镜不适调整结果：" + nz(req.getAdjustResult())
                        + (c.getReviewAdvised() ? "；继续提醒按时复查" : "；继续跟踪戴镜适应"));
        return c;
    }

    /** 适应回访结果登记；已适应则闭环不适与回访事项 */
    @Transactional
    public ComfortFollowup submitCallback(VerificationDtos.CallbackRequest req) {
        ComfortFollowup c = comfortRepo.findById(req.getFollowupId())
                .orElseThrow(() -> new BusinessException("售后跟进记录不存在"));
        c.setCallbackDate(req.getCallbackDate() == null ? LocalDate.now() : req.getCallbackDate());
        c.setCallbackResult(req.getCallbackResult());
        c.setResolved(Boolean.TRUE.equals(req.getResolved()));
        c.setCallbackStatus("DONE");
        comfortRepo.save(c);

        if (c.getResolved()) {
            c.getGlasses().setDiscomfortResolved(true);
            glassesRepo.save(c.getGlasses());
            closeIssue(c.getStudent(), ScreenFlag.GLASSES_DISCOMFORT,
                    "门店调整后适应回访确认：" + nz(req.getCallbackResult()));
            closeIssue(c.getStudent(), ScreenFlag.ADAPT_FOLLOWUP,
                    "适应回访已完成：" + nz(req.getCallbackResult()));
        }
        timeline.event(c.getStudent(), EventType.COMFORT,
                "配镜适应回访完成（" + c.getCallbackDate() + "）：" + nz(req.getCallbackResult())
                        + (c.getResolved() ? "；学生已适应，不适事项闭环" : "；仍有不适，继续跟进并提醒复查"));
        return c;
    }

    /* ================= 辅助 ================= */

    /** 单项比对：0=一致/不适用，1=门店缺项，2=不一致 */
    private int compareOne(String label, String storeVal, String refVal, List<String> line) {
        String a = norm(storeVal);
        String b = norm(refVal);
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) {
            line.add(label + " 门店缺项（参考值 " + nz(refVal) + "）");
            return 1;
        }
        if (b.isEmpty()) return 0; // 参考方无该项，不做要求
        if (!a.equals(b)) {
            line.add(label + " 门店 " + storeVal + " ≠ 参考值 " + refVal);
            return 2;
        }
        return 0;
    }

    private String mismatchItems(List<String> line) {
        return String.join("、", line);
    }

    /** 参数归一化：去空格、去度数单位 D、去正号，便于 "-1.75D" 与 "-1.75" 逐项对应 */
    private String norm(String s) {
        if (s == null) return "";
        String t = s.trim().replace(" ", "").replace("　", "")
                .replaceAll("[Dd]$", "").replace("+", "").replace("＋", "");
        try {
            double d = Double.parseDouble(t);
            if (d == 0) return "0";
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return t;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private String money(BigDecimal b) {
        return b == null ? "—" : b.stripTrailingZeros().toPlainString() + " 元";
    }

    private String buildLensSummary(VerificationDtos.StoreGlassesRequest req) {
        return nz(req.getLensBrand()) + " 球镜 " + nz(req.getLensSphereLeft()) + "/" + nz(req.getLensSphereRight())
                + " 瞳距 " + nz(req.getLensPd());
    }

    private void closeHoldIssue(Student s, String note) {
        closeIssue(s, ScreenFlag.VERIFICATION_HOLD, note);
    }

    private void closeIssue(Student s, ScreenFlag flag, String note) {
        issueRepo.findFirstByStudentIdAndIssueTypeAndStatus(s.getId(), flag.name(), "OPEN")
                .ifPresent(it -> timeline.resolveIssue(it.getId(), note));
    }
}
