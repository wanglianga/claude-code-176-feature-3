package com.county.vision.service;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.EventType;
import com.county.vision.domain.enums.InterventionType;
import com.county.vision.domain.enums.SubsidyStatus;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.StudentDtos;
import com.county.vision.security.LoginUser;
import com.county.vision.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 学生完整健康管理档案聚合：一条记录串起筛查→告知→复诊→处方→配镜→补助→复查→干预。
 *  统一输出 Map 投影，避免 open-in-view=false 下懒加载序列化异常。 */
@Service
public class QueryService {

    private final StudentRepository studentRepo;
    private final ScreenRecordRepository screenRepo;
    private final ParentConfirmationRepository confirmRepo;
    private final SchoolReviewRepository reviewRepo;
    private final VisitRecordRepository visitRepo;
    private final GlassesRecordRepository glassesRepo;
    private final SubsidyApplicationRepository subsidyRepo;
    private final ReviewFollowUpRepository followupRepo;
    private final InterventionRepository interventionRepo;
    private final IssueTrackerRepository issueRepo;
    private final HealthEventRepository eventRepo;
    private final SubsidyVerificationRepository verificationRepo;
    private final AfterSalesRepository afterSalesRepo;
    private final ComfortFollowupRepository comfortRepo;

    public QueryService(StudentRepository studentRepo, ScreenRecordRepository screenRepo,
                        ParentConfirmationRepository confirmRepo, SchoolReviewRepository reviewRepo,
                        VisitRecordRepository visitRepo, GlassesRecordRepository glassesRepo,
                        SubsidyApplicationRepository subsidyRepo, ReviewFollowUpRepository followupRepo,
                        InterventionRepository interventionRepo, IssueTrackerRepository issueRepo,
                        HealthEventRepository eventRepo,
                        SubsidyVerificationRepository verificationRepo,
                        AfterSalesRepository afterSalesRepo,
                        ComfortFollowupRepository comfortRepo) {
        this.studentRepo = studentRepo;
        this.screenRepo = screenRepo;
        this.confirmRepo = confirmRepo;
        this.reviewRepo = reviewRepo;
        this.visitRepo = visitRepo;
        this.glassesRepo = glassesRepo;
        this.subsidyRepo = subsidyRepo;
        this.followupRepo = followupRepo;
        this.interventionRepo = interventionRepo;
        this.issueRepo = issueRepo;
        this.eventRepo = eventRepo;
        this.verificationRepo = verificationRepo;
        this.afterSalesRepo = afterSalesRepo;
        this.comfortRepo = comfortRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Student s = studentRepo.findById(id).orElseThrow(() -> new BusinessException("学生不存在"));
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null && !me.getSchoolId().equals(s.getSchool().getId())) {
            throw new BusinessException(403, "无权查看外校学生");
        }

        Map<String, Object> d = baseStudent(s);
        d.put("issues", issueRepo.findByStudentIdOrderByRaisedAtDesc(id).stream().map(this::issueMap).toList());
        d.put("latestScreen", screenRepo.findFirstByStudentIdOrderByScreenDateDescIdDesc(id)
                .map(this::screenMap).orElse(null));
        d.put("confirmation", confirmRepo.findFirstByStudentIdOrderByIdDesc(id)
                .map(this::confirmMap).orElse(null));
        d.put("schoolReview", reviewRepo.findFirstByStudentIdOrderByIdDesc(id)
                .map(this::reviewMap).orElse(null));
        d.put("visits", visitRepo.findByStudentIdOrderByVisitDateAscIdAsc(id).stream().map(this::visitMap).toList());
        d.put("glasses", glassesRepo.findByStudentIdOrderByGlassesDateAscIdAsc(id).stream().map(this::glassesMap).toList());
        d.put("subsidies", subsidyRepo.findFirstByStudentIdOrderByIdDesc(id)
                .map(a -> List.of(subsidyMap(a))).orElse(List.of()));
        d.put("verifications", verificationRepo.findByStudentIdOrderByIdDesc(id).stream()
                .map(this::verificationMap).toList());
        d.put("afterSales", afterSalesRepo.findByStudentIdOrderByIdDesc(id).stream()
                .map(this::afterSalesMap).toList());
        d.put("comfortFollowups", comfortRepo.findByStudentIdOrderByFeedbackDateDescIdDesc(id).stream()
                .map(this::comfortMap).toList());
        d.put("followups", followupRepo.findByStudentIdOrderByPlannedDateAscIdAsc(id).stream().map(this::followMap).toList());
        d.put("interventions", interventionRepo.findByStudentIdOrderByExecDateDescIdDesc(id)
                .stream().map(this::interventionMap).toList());
        d.put("events", eventRepo.findByStudentIdOrderByEventTimeAscIdAsc(id).stream().map(this::eventMap).toList());
        return d;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeline(Long studentId) {
        return eventRepo.findByStudentIdOrderByEventTimeAscIdAsc(studentId).stream().map(this::eventMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> issues(String status) {
        List<IssueTracker> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? issueRepo.findAll() : issueRepo.findByStatusOrderByRaisedAtDesc(status);
        return list.stream().map(this::issueMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> followups(String status) {
        List<ReviewFollowUp> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? followupRepo.findAll() : followupRepo.findByStatusOrderByPlannedDateAsc(status);
        return list.stream().map(this::followMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> subsidies(String status) {
        List<SubsidyApplication> list;
        if (status == null || status.isBlank() || "ALL".equals(status)) {
            list = subsidyRepo.findAll();
        } else {
            try {
                list = subsidyRepo.findByStatusOrderByIdDesc(SubsidyStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                list = subsidyRepo.findAll();
            }
        }
        return list.stream().map(this::subsidyMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> verifications(String status) {
        List<SubsidyVerification> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? verificationRepo.findAllByOrderByIdDesc()
                : verificationRepo.findByStatusOrderByIdDesc(
                        com.county.vision.domain.enums.VerificationStatus.valueOf(status));
        return list.stream().map(this::verificationMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verificationDetail(Long id) {
        SubsidyVerification v = verificationRepo.findById(id)
                .orElseThrow(() -> new BusinessException("核验单不存在"));
        return verificationMap(v);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verificationByGlasses(Long glassesId) {
        return verificationRepo.findByGlassesId(glassesId).map(this::verificationMap)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> afterSalesList(String status) {
        List<AfterSales> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? afterSalesRepo.findAllByOrderByIdDesc()
                : afterSalesRepo.findByStatusOrderByIdDesc(status);
        return list.stream().map(this::afterSalesMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> comfortList(String status) {
        List<ComfortFollowup> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? comfortRepo.findAllByOrderByIdDesc()
                : comfortRepo.findByCallbackStatusOrderByCallbackDueDateAsc(status);
        return list.stream().map(this::comfortMap).toList();
    }

    /* ----------------------- 投影 ----------------------- */

    private Map<String, Object> baseStudent(Student s) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", s.getId());
        d.put("studentNo", s.getStudentNo());
        d.put("name", s.getName());
        d.put("gender", s.getGender());
        d.put("birthDate", s.getBirthDate());
        d.put("schoolId", s.getSchool().getId());
        d.put("schoolName", s.getSchool().getName());
        d.put("regionName", s.getSchool().getRegion() != null ? s.getSchool().getRegion().getName() : null);
        d.put("classId", s.getGradeClass().getId());
        d.put("grade", s.getGradeClass().getGrade());
        d.put("className", s.getGradeClass().getClassName());
        d.put("parentName", s.getParentName());
        d.put("parentPhone", s.getParentPhone());
        d.put("priorVisionArchive", s.getPriorVisionArchive());
        d.put("screenYear", s.getScreenYear());
        d.put("stage", s.getStage().name());
        d.put("stageLabel", s.getStage().getLabel());
        d.put("flags", TimelineService.splitFlags(s.getFlags()));
        d.put("flagLabels", TimelineService.splitFlags(s.getFlags()).stream().map(TimelineService::labelOf).toList());
        d.put("importError", s.getImportError());
        d.put("importErrorDetail", s.getImportErrorDetail());
        d.put("createdAt", s.getCreatedAt());
        return d;
    }

    private Map<String, Object> screenMap(ScreenRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("screenDate", r.getScreenDate());
        m.put("orgName", r.getOrgName());
        m.put("operatorName", r.getOperatorName());
        m.put("deviceCalibNo", r.getDeviceCalibNo());
        m.put("nakedVisionLeft", r.getNakedVisionLeft());
        m.put("nakedVisionRight", r.getNakedVisionRight());
        m.put("sphereLeft", r.getSphereLeft());
        m.put("sphereRight", r.getSphereRight());
        m.put("cylinderLeft", r.getCylinderLeft());
        m.put("cylinderRight", r.getCylinderRight());
        m.put("wearingGlasses", r.getWearingGlasses());
        m.put("habitSummary", r.getHabitSummary());
        m.put("retestNeeded", r.getRetestNeeded());
        m.put("abnormal", r.getAbnormal());
        m.put("abnormalReason", r.getAbnormalReason());
        m.put("absent", r.getAbsent());
        return m;
    }

    private Map<String, Object> confirmMap(ParentConfirmation c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("notified", c.getNotified());
        m.put("notifyChannel", c.getNotifyChannel());
        m.put("notifyTime", c.getNotifyTime());
        m.put("unreachableCount", c.getUnreachableCount());
        m.put("confirmed", c.getConfirmed());
        m.put("confirmTime", c.getConfirmTime());
        m.put("confirmerName", c.getConfirmerName());
        m.put("choice", c.getChoice() == null ? null : c.getChoice().name());
        m.put("choiceLabel", c.getChoice() == null ? null : c.getChoice().getLabel());
        m.put("targetOrg", c.getTargetOrg());
        m.put("deferReason", c.getDeferReason());
        m.put("remark", c.getRemark());
        return m;
    }

    private Map<String, Object> reviewMap(SchoolReview r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("reviewer", r.getReviewer());
        m.put("reviewTime", r.getReviewTime());
        m.put("result", r.getResult());
        m.put("opinion", r.getOpinion());
        return m;
    }

    private Map<String, Object> visitMap(VisitRecord v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("orgName", v.getOrgName());
        m.put("doctor", v.getDoctor());
        m.put("visitDate", v.getVisitDate());
        m.put("trueMyopia", v.getTrueMyopia());
        m.put("needMydriasis", v.getNeedMydriasis());
        m.put("rxSphereLeft", v.getRxSphereLeft());
        m.put("rxSphereRight", v.getRxSphereRight());
        m.put("rxCylinderLeft", v.getRxCylinderLeft());
        m.put("rxCylinderRight", v.getRxCylinderRight());
        m.put("rxAxisLeft", v.getRxAxisLeft());
        m.put("rxAxisRight", v.getRxAxisRight());
        m.put("pd", v.getPd());
        m.put("diagnosis", v.getDiagnosis());
        m.put("reviewCycleMonths", v.getReviewCycleMonths());
        m.put("subsidyEligible", v.getSubsidyEligible());
        m.put("bigDifference", v.getBigDifference());
        m.put("differenceNote", v.getDifferenceNote());
        return m;
    }

    private Map<String, Object> glassesMap(GlassesRecord g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("opticianOrg", g.getOpticianOrg());
        m.put("glassesDate", g.getGlassesDate());
        m.put("partnerOptician", g.getPartnerOptician());
        m.put("amount", g.getAmount());
        m.put("lensInfo", g.getLensInfo());
        // 门店上传材料
        m.put("lensBrand", g.getLensBrand());
        m.put("rxNo", g.getRxNo());
        m.put("rxSphereLeft", g.getRxSphereLeft());
        m.put("rxSphereRight", g.getRxSphereRight());
        m.put("rxCylinderLeft", g.getRxCylinderLeft());
        m.put("rxCylinderRight", g.getRxCylinderRight());
        m.put("rxAxisLeft", g.getRxAxisLeft());
        m.put("rxAxisRight", g.getRxAxisRight());
        m.put("rxPd", g.getRxPd());
        m.put("lensSphereLeft", g.getLensSphereLeft());
        m.put("lensSphereRight", g.getLensSphereRight());
        m.put("lensCylinderLeft", g.getLensCylinderLeft());
        m.put("lensCylinderRight", g.getLensCylinderRight());
        m.put("lensAxisLeft", g.getLensAxisLeft());
        m.put("lensAxisRight", g.getLensAxisRight());
        m.put("lensPd", g.getLensPd());
        m.put("paymentVoucherNo", g.getPaymentVoucherNo());
        m.put("paymentVoucherFile", g.getPaymentVoucherFile());
        m.put("pickedUp", g.getPickedUp());
        m.put("pickupDate", g.getPickupDate());
        m.put("receiverName", g.getReceiverName());
        m.put("pickupRemark", g.getPickupRemark());
        m.put("discomfort", g.getDiscomfort());
        m.put("discomfortType", g.getDiscomfortType());
        m.put("discomfortDetail", g.getDiscomfortDetail());
        m.put("discomfortResolved", g.getDiscomfortResolved());
        return m;
    }

    private Map<String, Object> verificationMap(SubsidyVerification v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("studentId", v.getStudent().getId());
        m.put("studentName", v.getStudent().getName());
        m.put("studentNo", v.getStudent().getStudentNo());
        m.put("schoolName", v.getStudent().getSchool().getName());
        m.put("glassesId", v.getGlasses() == null ? null : v.getGlasses().getId());
        m.put("opticianOrg", v.getGlasses() == null ? null : v.getGlasses().getOpticianOrg());
        m.put("glassesDate", v.getGlasses() == null ? null : v.getGlasses().getGlassesDate());
        m.put("glassesAmount", v.getGlasses() == null ? null : v.getGlasses().getAmount());
        m.put("paymentVoucherNo", v.getGlasses() == null ? null : v.getGlasses().getPaymentVoucherNo());
        m.put("subsidyId", v.getSubsidy() == null ? null : v.getSubsidy().getId());
        m.put("status", v.getStatus().name());
        m.put("statusLabel", v.getStatus().getLabel());
        m.put("studentCheck", check(v.getStudentCheck()));
        m.put("prescriptionCheck", check(v.getPrescriptionCheck()));
        m.put("lensParamCheck", check(v.getLensParamCheck()));
        m.put("eligibilityCheck", check(v.getEligibilityCheck()));
        m.put("voucherCheck", check(v.getVoucherCheck()));
        m.put("pickupCheck", check(v.getPickupCheck()));
        m.put("standardCheck", check(v.getStandardCheck()));
        m.put("holdReasons", v.getHoldReasons());
        m.put("checkDetail", v.getCheckDetail());
        m.put("standardAmount", v.getStandardAmount());
        m.put("payableAmount", v.getPayableAmount());
        m.put("paidAmount", v.getPaidAmount());
        m.put("submitter", v.getSubmitter());
        m.put("submitTime", v.getSubmitTime());
        m.put("reviewer", v.getReviewer());
        m.put("reviewTime", v.getReviewTime());
        m.put("paidTime", v.getPaidTime());
        m.put("opinion", v.getOpinion());
        return m;
    }

    private Map<String, Object> check(com.county.vision.domain.enums.CheckResult c) {
        if (c == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", c.name());
        m.put("label", c.getLabel());
        return m;
    }

    private Map<String, Object> afterSalesMap(AfterSales a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("studentId", a.getStudent().getId());
        m.put("studentName", a.getStudent().getName());
        m.put("schoolName", a.getStudent().getSchool().getName());
        m.put("glassesId", a.getGlasses().getId());
        m.put("opticianOrg", a.getGlasses().getOpticianOrg());
        m.put("verificationId", a.getVerification().getId());
        m.put("subsidyId", a.getSubsidy() == null ? null : a.getSubsidy().getId());
        m.put("type", a.getType().name());
        m.put("typeLabel", a.getType().getLabel());
        m.put("applyDate", a.getApplyDate());
        m.put("processedDate", a.getProcessedDate());
        m.put("refundAmount", a.getRefundAmount());
        m.put("newCostAmount", a.getNewCostAmount());
        m.put("settledCostAmount", a.getSettledCostAmount());
        m.put("originalSubsidy", a.getOriginalSubsidy());
        m.put("recalculatedSubsidy", a.getRecalculatedSubsidy());
        m.put("settleAmount", a.getSettleAmount());
        m.put("direction", a.getDirection().name());
        m.put("directionLabel", a.getDirection().getLabel());
        m.put("status", a.getStatus());
        m.put("settleVoucherNo", a.getSettleVoucherNo());
        m.put("settleDate", a.getSettleDate());
        m.put("handler", a.getHandler());
        m.put("remark", a.getRemark());
        return m;
    }

    private Map<String, Object> comfortMap(ComfortFollowup c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("studentId", c.getStudent().getId());
        m.put("studentName", c.getStudent().getName());
        m.put("schoolName", c.getStudent().getSchool().getName());
        m.put("glassesId", c.getGlasses().getId());
        m.put("opticianOrg", c.getGlasses().getOpticianOrg());
        m.put("discomfortType", c.getDiscomfortType());
        try {
            m.put("discomfortTypeLabel",
                    com.county.vision.domain.enums.DiscomfortType.valueOf(c.getDiscomfortType()).getLabel());
        } catch (Exception e) {
            m.put("discomfortTypeLabel", c.getDiscomfortType());
        }
        m.put("feedbackDate", c.getFeedbackDate());
        m.put("feedback", c.getFeedback());
        m.put("adjustResult", c.getAdjustResult());
        m.put("adjustDate", c.getAdjustDate());
        m.put("reviewAdvised", c.getReviewAdvised());
        m.put("reviewPlannedDate", c.getReviewPlannedDate());
        m.put("callbackDueDate", c.getCallbackDueDate());
        m.put("callbackDate", c.getCallbackDate());
        m.put("callbackResult", c.getCallbackResult());
        m.put("callbackStatus", c.getCallbackStatus());
        m.put("resolved", c.getResolved());
        m.put("handler", c.getHandler());
        return m;
    }

    private Map<String, Object> subsidyMap(SubsidyApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("studentId", a.getStudent().getId());
        m.put("studentName", a.getStudent().getName());
        m.put("schoolName", a.getStudent().getSchool().getName());
        m.put("glassesId", a.getGlasses() == null ? null : a.getGlasses().getId());
        m.put("applyAmount", a.getApplyAmount());
        m.put("approvedAmount", a.getApprovedAmount());
        m.put("status", a.getStatus().name());
        m.put("statusLabel", a.getStatus().getLabel());
        m.put("applicant", a.getApplicant());
        m.put("applyTime", a.getApplyTime());
        m.put("approver", a.getApprover());
        m.put("approveTime", a.getApproveTime());
        m.put("opinion", a.getOpinion());
        return m;
    }

    private Map<String, Object> followMap(ReviewFollowUp f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("studentId", f.getStudent().getId());
        m.put("studentName", f.getStudent().getName());
        m.put("schoolName", f.getStudent().getSchool().getName());
        m.put("plannedDate", f.getPlannedDate());
        m.put("actualDate", f.getActualDate());
        m.put("status", f.getStatus());
        m.put("orgName", f.getOrgName());
        m.put("visionResult", f.getVisionResult());
        m.put("remark", f.getRemark());
        m.put("remindCount", f.getRemindCount());
        return m;
    }

    private Map<String, Object> interventionMap(Intervention iv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", iv.getId());
        m.put("type", iv.getType().name());
        m.put("typeLabel", iv.getType().getLabel());
        m.put("execDate", iv.getExecDate());
        m.put("executor", iv.getExecutor());
        m.put("content", iv.getContent());
        m.put("executed", iv.getExecuted());
        return m;
    }

    private Map<String, Object> issueMap(IssueTracker i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("studentId", i.getStudent().getId());
        m.put("studentName", i.getStudent().getName());
        m.put("schoolName", i.getStudent().getSchool().getName());
        m.put("issueType", i.getIssueType());
        try {
            m.put("issueTypeLabel", com.county.vision.domain.enums.ScreenFlag.valueOf(i.getIssueType()).getLabel());
        } catch (Exception e) {
            m.put("issueTypeLabel", i.getIssueType());
        }
        m.put("description", i.getDescription());
        m.put("status", i.getStatus());
        m.put("raisedBy", i.getRaisedBy());
        m.put("raisedAt", i.getRaisedAt());
        m.put("resolvedBy", i.getResolvedBy());
        m.put("resolvedAt", i.getResolvedAt());
        m.put("resolveNote", i.getResolveNote());
        return m;
    }

    private Map<String, Object> eventMap(HealthEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("type", e.getType().name());
        m.put("typeLabel", e.getType().getLabel());
        m.put("eventTime", e.getEventTime());
        m.put("content", e.getContent());
        m.put("actor", e.getActor());
        return m;
    }
}
