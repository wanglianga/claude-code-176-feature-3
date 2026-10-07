package com.county.vision.service;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.*;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.RecordDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康管理主流程：
 * 筛查 → 学校复核 → 家长告知/确认 → 复诊（处方）→ 配镜 → 补助 → 复查 → 学校干预
 * 每一步都写入健康时间线，并对异常情形打开/闭环异常事项。
 */
@Service
public class FlowService {

    private final StudentRepository studentRepo;
    private final ScreenRecordRepository screenRepo;
    private final SchoolReviewRepository reviewRepo;
    private final ParentConfirmationRepository confirmRepo;
    private final VisitRecordRepository visitRepo;
    private final GlassesRecordRepository glassesRepo;
    private final SubsidyApplicationRepository subsidyRepo;
    private final ReviewFollowUpRepository followupRepo;
    private final InterventionRepository interventionRepo;
    private final IssueTrackerRepository issueRepo;
    private final TimelineService timeline;

    public FlowService(StudentRepository studentRepo, ScreenRecordRepository screenRepo,
                       SchoolReviewRepository reviewRepo, ParentConfirmationRepository confirmRepo,
                       VisitRecordRepository visitRepo, GlassesRecordRepository glassesRepo,
                       SubsidyApplicationRepository subsidyRepo, ReviewFollowUpRepository followupRepo,
                       InterventionRepository interventionRepo, IssueTrackerRepository issueRepo,
                       TimelineService timeline) {
        this.studentRepo = studentRepo;
        this.screenRepo = screenRepo;
        this.reviewRepo = reviewRepo;
        this.confirmRepo = confirmRepo;
        this.visitRepo = visitRepo;
        this.glassesRepo = glassesRepo;
        this.subsidyRepo = subsidyRepo;
        this.followupRepo = followupRepo;
        this.interventionRepo = interventionRepo;
        this.issueRepo = issueRepo;
        this.timeline = timeline;
    }

    private Student requireStudent(Long id) {
        return studentRepo.findById(id).orElseThrow(() -> new BusinessException("学生不存在"));
    }

    /* ================= 1. 筛查现场记录 ================= */

    @Transactional
    public ScreenRecord submitScreen(RecordDtos.ScreenRequest req) {
        Student s = requireStudent(req.getStudentId());
        ScreenRecord rec = new ScreenRecord();
        rec.setStudent(s);
        rec.setScreenDate(req.getScreenDate() == null ? LocalDate.now() : req.getScreenDate());
        rec.setOrgName(req.getOrgName());
        rec.setOperatorName(req.getOperatorName());
        rec.setDeviceCalibNo(req.getDeviceCalibNo());
        rec.setNakedVisionLeft(req.getNakedVisionLeft());
        rec.setNakedVisionRight(req.getNakedVisionRight());
        rec.setSphereLeft(req.getSphereLeft());
        rec.setSphereRight(req.getSphereRight());
        rec.setCylinderLeft(req.getCylinderLeft());
        rec.setCylinderRight(req.getCylinderRight());
        rec.setWearingGlasses(Boolean.TRUE.equals(req.getWearingGlasses()));
        rec.setHabitSummary(req.getHabitSummary());
        rec.setRetestNeeded(Boolean.TRUE.equals(req.getRetestNeeded()));

        boolean absent = Boolean.TRUE.equals(req.getAbsent());
        rec.setAbsent(absent);
        boolean abnormal = !absent && judgeAbnormal(req);
        rec.setAbnormal(abnormal);
        if (abnormal) {
            rec.setAbnormalReason(buildAbnormalReason(req));
        }
        ScreenRecord saved = screenRepo.save(rec);

        if (absent) {
            timeline.raiseIssue(s, ScreenFlag.ABSENT, "年度筛查当日请假未检，需补检");
            timeline.event(s, EventType.SCREEN, "筛查请假，未完成现场检查，已登记补检事项");
            timeline.changeStage(s, StudentStage.LOST_FOLLOW);
            return saved;
        }

        timeline.event(s, EventType.SCREEN,
                String.format("完成视力筛查：裸眼视力 左%s/右%s，球镜 左%s/右%s，散光 左%s/右%s，戴镜=%s，设备校准编号=%s%s",
                        nz(req.getNakedVisionLeft()), nz(req.getNakedVisionRight()),
                        nz(req.getSphereLeft()), nz(req.getSphereRight()),
                        nz(req.getCylinderLeft()), nz(req.getCylinderRight()),
                        rec.getWearingGlasses() ? "是" : "否", nz(req.getDeviceCalibNo()),
                        abnormal ? "，判定异常" : ""));

        if (abnormal) {
            timeline.changeStage(s, StudentStage.ABNORMAL_REVIEW);
            if (rec.getRetestNeeded()) {
                timeline.raiseIssue(s, ScreenFlag.RETEST_NEEDED, "筛查机构建议复测");
            }
        } else {
            timeline.changeStage(s, StudentStage.SCREENED);
        }
        return saved;
    }

    /** 简单判定：任一眼裸眼视力低于 5.0(标准对数) 或 球镜 <= -0.50D 视为异常 */
    private boolean judgeAbnormal(RecordDtos.ScreenRequest req) {
        if (belowVision(req.getNakedVisionLeft()) || belowVision(req.getNakedVisionRight())) return true;
        if (myopicSphere(req.getSphereLeft()) || myopicSphere(req.getSphereRight())) return true;
        return false;
    }

    private boolean belowVision(String v) {
        if (v == null || v.isBlank()) return false;
        try {
            double d = Double.parseDouble(v.trim());
            // 兼容 5 分记录（4.9 以下）和小数记录（1.0 以下）
            return (d > 2 && d < 4.9) || (d <= 2 && d < 1.0);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean myopicSphere(String sph) {
        if (sph == null || sph.isBlank()) return false;
        try {
            return Double.parseDouble(sph.replace("D", "").trim()) <= -0.50;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildAbnormalReason(RecordDtos.ScreenRequest req) {
        StringBuilder sb = new StringBuilder();
        if (belowVision(req.getNakedVisionLeft()) || belowVision(req.getNakedVisionRight())) {
            sb.append("裸眼视力低于4.9；");
        }
        if (myopicSphere(req.getSphereLeft()) || myopicSphere(req.getSphereRight())) {
            sb.append("球镜度数提示近视；");
        }
        return sb.toString();
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /* ================= 2. 学校健康老师复核 ================= */

    @Transactional
    public SchoolReview schoolReview(RecordDtos.SchoolReviewRequest req) {
        Student s = requireStudent(req.getStudentId());
        SchoolReview r = new SchoolReview();
        r.setStudent(s);
        r.setReviewer(com.county.vision.security.SecurityUtils.actorLabel());
        r.setReviewTime(LocalDateTime.now());
        r.setResult(req.getResult());
        r.setOpinion(req.getOpinion());
        SchoolReview saved = reviewRepo.save(r);

        switch (req.getResult()) {
            case "CONFIRM" -> {
                timeline.event(s, EventType.SCHOOL_REVIEW, "学校健康老师复核确认筛查异常：" + nz(req.getOpinion()));
                timeline.changeStage(s, StudentStage.AWAIT_PARENT);
            }
            case "REJECT" -> {
                timeline.event(s, EventType.SCHOOL_REVIEW, "学校复核排除异常（误判/档案已矫正）：" + nz(req.getOpinion()));
                timeline.changeStage(s, StudentStage.SCREENED);
            }
            case "RETEST" -> {
                timeline.raiseIssue(s, ScreenFlag.RETEST_NEEDED, "学校复核建议复测：" + nz(req.getOpinion()));
                timeline.event(s, EventType.SCHOOL_REVIEW, "学校复核建议安排复测");
            }
            default -> throw new BusinessException("复核结果只能为 CONFIRM/REJECT/RETEST");
        }
        return saved;
    }

    /* ================= 3. 家长告知 ================= */

    @Transactional
    public ParentConfirmation notifyParent(RecordDtos.NotifyRequest req) {
        Student s = requireStudent(req.getStudentId());
        ParentConfirmation pc = confirmRepo.findFirstByStudentIdOrderByIdDesc(s.getId())
                .filter(c -> !Boolean.TRUE.equals(c.getConfirmed()))
                .orElseGet(() -> {
                    ParentConfirmation c = new ParentConfirmation();
                    c.setStudent(s);
                    return c;
                });
        pc.setNotifyChannel(req.getChannel());
        if (Boolean.TRUE.equals(req.getUnreachable())) {
            pc.setUnreachableCount((pc.getUnreachableCount() == null ? 0 : pc.getUnreachableCount()) + 1);
            timeline.raiseIssue(s, ScreenFlag.UNREACHABLE,
                    "家长电话无法接通（累计第" + pc.getUnreachableCount() + "次）");
            timeline.event(s, EventType.NOTIFY,
                    "通过" + nz(req.getChannel()) + "告知家长，电话无法接通（累计第" + pc.getUnreachableCount() + "次）");
            timeline.changeStage(s, StudentStage.LOST_FOLLOW);
        } else {
            pc.setNotified(true);
            pc.setNotifyTime(LocalDateTime.now());
            timeline.event(s, EventType.NOTIFY, "已通过" + nz(req.getChannel()) + "向家长告知筛查异常结果"
                    + (req.getRemark() == null ? "" : "：" + req.getRemark()));
        }
        return confirmRepo.save(pc);
    }

    /* ================= 4. 家长确认与选择 ================= */

    @Transactional
    public ParentConfirmation parentChoice(RecordDtos.ParentChoiceRequest req) {
        Student s = requireStudent(req.getStudentId());
        ParentChoice choice;
        try {
            choice = ParentChoice.valueOf(req.getChoice());
        } catch (Exception e) {
            throw new BusinessException("家长选择只能为 COUNTY_HOSPITAL/PARTNER_CLINIC/DEFER");
        }
        if (choice == ParentChoice.DEFER && (req.getDeferReason() == null || req.getDeferReason().isBlank())) {
            throw new BusinessException("选择暂缓处理时必须填写原因");
        }
        ParentConfirmation pc = confirmRepo.findFirstByStudentIdOrderByIdDesc(s.getId())
                .orElseGet(() -> {
                    ParentConfirmation c = new ParentConfirmation();
                    c.setStudent(s);
                    return c;
                });
        pc.setConfirmed(true);
        pc.setConfirmTime(LocalDateTime.now());
        pc.setConfirmerName(s.getParentName());
        pc.setChoice(choice);
        pc.setTargetOrg(req.getTargetOrg());
        pc.setDeferReason(req.getDeferReason());
        pc.setRemark(req.getRemark());
        if (!Boolean.TRUE.equals(pc.getNotified())) {
            pc.setNotified(true);
            pc.setNotifyTime(pc.getConfirmTime());
        }
        ParentConfirmation saved = confirmRepo.save(pc);

        // 电话接通确认后，闭环“无法接通”事项
        issueCloseIfOpen(s, ScreenFlag.UNREACHABLE, "家长已接通并完成确认");

        timeline.event(s, EventType.PARENT_CONFIRM, "家长确认异常结果，选择：" + choice.getLabel()
                + (req.getTargetOrg() == null ? "" : "（" + req.getTargetOrg() + "）")
                + (choice == ParentChoice.DEFER ? "，暂缓原因：" + req.getDeferReason() : ""));

        switch (choice) {
            case COUNTY_HOSPITAL, PARTNER_CLINIC -> timeline.changeStage(s, StudentStage.AWAIT_VISIT);
            case DEFER -> timeline.changeStage(s, StudentStage.DEFERRED);
        }
        return saved;
    }

    /* ================= 5. 医院复诊回写 ================= */

    @Transactional
    public VisitRecord submitVisit(RecordDtos.VisitRequest req) {
        Student s = requireStudent(req.getStudentId());
        VisitRecord v = new VisitRecord();
        v.setStudent(s);
        v.setOrgName(req.getOrgName());
        v.setDoctor(req.getDoctor());
        v.setVisitDate(req.getVisitDate() == null ? LocalDate.now() : req.getVisitDate());
        v.setTrueMyopia(req.getTrueMyopia());
        v.setNeedMydriasis(Boolean.TRUE.equals(req.getNeedMydriasis()));
        v.setRxSphereLeft(req.getRxSphereLeft());
        v.setRxSphereRight(req.getRxSphereRight());
        v.setRxCylinderLeft(req.getRxCylinderLeft());
        v.setRxCylinderRight(req.getRxCylinderRight());
        v.setRxAxisLeft(req.getRxAxisLeft());
        v.setRxAxisRight(req.getRxAxisRight());
        v.setPd(req.getPd());
        v.setDiagnosis(req.getDiagnosis());
        v.setReviewCycleMonths(req.getReviewCycleMonths());
        v.setSubsidyEligible(Boolean.TRUE.equals(req.getSubsidyEligible()));
        v.setBigDifference(Boolean.TRUE.equals(req.getBigDifference()));
        v.setDifferenceNote(req.getDifferenceNote());
        VisitRecord saved = visitRepo.save(v);

        timeline.event(s, EventType.VISIT,
                (v.getOrgName() == null ? "医疗机构" : v.getOrgName()) + "复诊：真性近视="
                        + boolCn(req.getTrueMyopia()) + "，需散瞳=" + boolCn(v.getNeedMydriasis())
                        + "，诊断：" + nz(req.getDiagnosis())
                        + "，建议复查周期 " + (req.getReviewCycleMonths() == null ? "—" : req.getReviewCycleMonths()) + " 个月");
        timeline.event(s, EventType.PRESCRIPTION,
                "处方参数：球镜 左" + nz(v.getRxSphereLeft()) + "/右" + nz(v.getRxSphereRight())
                        + "，柱镜 左" + nz(v.getRxCylinderLeft()) + "/右" + nz(v.getRxCylinderRight())
                        + "，轴位 左" + nz(v.getRxAxisLeft()) + "/右" + nz(v.getRxAxisRight())
                        + "，瞳距 " + nz(v.getPd())
                        + "；医生初判配镜补助：" + (v.getSubsidyEligible() ? "符合" : "不符合"));

        if (v.getBigDifference()) {
            timeline.raiseIssue(s, ScreenFlag.DATA_CONFLICT,
                    "医院复诊结果与筛查差异较大：" + nz(req.getDifferenceNote()));
        } else {
            issueCloseIfOpen(s, ScreenFlag.DATA_CONFLICT, "复诊结果与筛查一致，差异已排除");
        }

        // 请假补检后完成复诊、暂缓后复诊都视作重新进入管理
        if (v.getSubsidyEligible()) {
            timeline.changeStage(s, StudentStage.GLASSES_SUBSIDY);
        } else {
            timeline.changeStage(s, StudentStage.VISITED);
        }

        // 自动建立下次复查计划
        if (req.getReviewCycleMonths() != null && req.getReviewCycleMonths() > 0) {
            ReviewFollowUp f = new ReviewFollowUp();
            f.setStudent(s);
            f.setPlannedDate(v.getVisitDate().plusMonths(req.getReviewCycleMonths()));
            f.setStatus("PLANNED");
            f.setOrgName(v.getOrgName());
            f.setRemark("医生建议的" + req.getReviewCycleMonths() + "个月周期复查");
            followupRepo.save(f);
            timeline.event(s, EventType.REVIEW, "已建立复查计划，应在 " + f.getPlannedDate() + " 前完成复查");
        }
        return saved;
    }

    /* ================= 6. 配镜登记 ================= */

    @Transactional
    public GlassesRecord submitGlasses(RecordDtos.GlassesRequest req) {
        Student s = requireStudent(req.getStudentId());
        GlassesRecord g = new GlassesRecord();
        g.setStudent(s);
        g.setOpticianOrg(req.getOpticianOrg());
        g.setGlassesDate(req.getGlassesDate() == null ? LocalDate.now() : req.getGlassesDate());
        g.setPartnerOptician(!Boolean.FALSE.equals(req.getPartnerOptician()));
        g.setAmount(req.getAmount());
        g.setLensInfo(req.getLensInfo());
        boolean discomfort = Boolean.TRUE.equals(req.getDiscomfort());
        g.setDiscomfort(discomfort);
        g.setDiscomfortDetail(req.getDiscomfortDetail());
        g.setDiscomfortResolved(false);
        GlassesRecord saved = glassesRepo.save(g);

        if (!g.getPartnerOptician()) {
            timeline.raiseIssue(s, ScreenFlag.EXTERNAL_GLASSES,
                    "家长自行在外配镜（机构：" + nz(req.getOpticianOrg()) + "），需核验合规凭证后才可申请补助");
            timeline.event(s, EventType.GLASSES,
                    "家长自行外配眼镜：" + nz(req.getOpticianOrg()) + "，金额 " + money(req.getAmount())
                            + "，已登记待核验");
        } else {
            timeline.event(s, EventType.GLASSES,
                    "合作机构配镜完成：" + nz(req.getOpticianOrg()) + "，金额 " + money(req.getAmount())
                            + "，镜片信息：" + nz(req.getLensInfo()));
        }
        if (discomfort) {
            timeline.raiseIssue(s, ScreenFlag.GLASSES_DISCOMFORT,
                    "配镜后不适：" + nz(req.getDiscomfortDetail()));
        }
        timeline.changeStage(s, StudentStage.MANAGING);
        return saved;
    }

    /** 登记配镜后不适的复诊调整结果 */
    @Transactional
    public GlassesRecord resolveDiscomfort(Long glassesId, String note) {
        GlassesRecord g = glassesRepo.findById(glassesId).orElseThrow(() -> new BusinessException("配镜记录不存在"));
        g.setDiscomfortResolved(true);
        if (note != null) {
            g.setDiscomfortDetail(g.getDiscomfortDetail() + "；调整：" + note);
        }
        glassesRepo.save(g);
        issueCloseIfOpen(g.getStudent(), ScreenFlag.GLASSES_DISCOMFORT, "已复诊调整：" + note);
        timeline.event(g.getStudent(), EventType.GLASSES, "配镜不适已复诊调整处理：" + nz(note));
        return g;
    }

    /* ================= 7. 配镜补助申请/核准/争议 ================= */

    @Transactional
    public SubsidyApplication applySubsidy(RecordDtos.SubsidyApplyRequest req) {
        Student s = requireStudent(req.getStudentId());
        GlassesRecord latest = glassesRepo.findByStudentIdOrderByGlassesDateAscIdAsc(s.getId())
                .stream().reduce((a, b) -> b).orElse(null);
        if (latest == null) {
            throw new BusinessException("尚无配镜记录，无法申请补助");
        }
        // 防重复申请：同一副眼镜（配镜记录）已有在办或已发放的补助时不得再次申请
        subsidyRepo.findByGlassesIdOrderByIdDesc(latest.getId()).stream()
                .filter(a -> a.getStatus() != SubsidyStatus.REJECTED && a.getStatus() != SubsidyStatus.RECOVERED)
                .findFirst().ifPresent(a -> {
                    throw new BusinessException("该配镜记录已存在补助申请（状态：" + a.getStatus().getLabel()
                            + "），请勿重复申请");
                });
        SubsidyApplication app = new SubsidyApplication();
        app.setStudent(s);
        app.setGlasses(latest);
        app.setApplyAmount(req.getApplyAmount() != null ? req.getApplyAmount() : latest.getAmount());
        app.setStatus(SubsidyStatus.APPLIED);
        app.setApplicant(com.county.vision.security.SecurityUtils.actorLabel());
        app.setApplyTime(LocalDateTime.now());
        app.setOpinion(req.getOpinion());
        SubsidyApplication saved = subsidyRepo.save(app);

        timeline.event(s, EventType.SUBSIDY,
                "提交配镜补助申请，金额 " + money(saved.getApplyAmount())
                        + (Boolean.FALSE.equals(latest.getPartnerOptician()) ? "（外配眼镜，待核验）" : ""));
        return saved;
    }

    @Transactional
    public SubsidyApplication approveSubsidy(RecordDtos.SubsidyApproveRequest req) {
        SubsidyApplication app = subsidyRepo.findById(req.getApplicationId())
                .orElseThrow(() -> new BusinessException("补助申请不存在"));
        Student s = app.getStudent();
        switch (req.getAction()) {
            case "APPROVED" -> {
                app.setStatus(SubsidyStatus.APPROVED);
                app.setApprovedAmount(req.getApprovedAmount() != null ? req.getApprovedAmount() : app.getApplyAmount());
                app.setOpinion(req.getOpinion());
                timeline.event(s, EventType.SUBSIDY,
                        "卫健部门核准补助 " + money(app.getApprovedAmount()) + "：" + nz(req.getOpinion()));
                issueCloseIfOpen(s, ScreenFlag.EXTERNAL_GLASSES, "外配眼镜凭证核验通过，补助核准");
            }
            case "PAID" -> {
                // 防二次发放：同一副眼镜已发放过补助时拦截
                if (app.getGlasses() != null
                        && subsidyRepo.existsByGlassesIdAndStatus(app.getGlasses().getId(), SubsidyStatus.PAID)) {
                    throw new BusinessException("该配镜记录已发放过补助，禁止重复发放");
                }
                app.setStatus(SubsidyStatus.PAID);
                if (req.getApprovedAmount() != null) app.setApprovedAmount(req.getApprovedAmount());
                app.setOpinion(req.getOpinion());
                timeline.event(s, EventType.SUBSIDY, "配镜补助已发放，金额 " + money(app.getApprovedAmount()));
                issueCloseIfOpen(s, ScreenFlag.SUBSIDY_DISPUTE, "争议处理完成，补助发放");
            }
            case "REJECTED" -> {
                app.setStatus(SubsidyStatus.REJECTED);
                app.setOpinion(req.getOpinion());
                timeline.event(s, EventType.SUBSIDY, "补助申请驳回：" + nz(req.getOpinion()));
            }
            case "DISPUTE" -> {
                app.setStatus(SubsidyStatus.DISPUTE);
                app.setOpinion(req.getOpinion());
                timeline.raiseIssue(s, ScreenFlag.SUBSIDY_DISPUTE,
                        "补助资格存在争议：" + nz(req.getOpinion()));
                timeline.event(s, EventType.SUBSIDY, "补助资格进入争议复核：" + nz(req.getOpinion()));
            }
            default -> throw new BusinessException("审批动作只能为 APPROVED/PAID/REJECTED/DISPUTE");
        }
        app.setApprover(com.county.vision.security.SecurityUtils.actorLabel());
        app.setApproveTime(LocalDateTime.now());
        return subsidyRepo.save(app);
    }

    /* ================= 8. 复查随访 ================= */

    @Transactional
    public ReviewFollowUp followUp(RecordDtos.FollowUpRequest req) {
        Student s = requireStudent(req.getStudentId());
        ReviewFollowUp f = followupRepo.findByStudentIdOrderByPlannedDateAscIdAsc(s.getId()).stream()
                .filter(x -> "PLANNED".equals(x.getStatus()) || "NO_SHOW".equals(x.getStatus()))
                .findFirst().orElseGet(() -> {
                    ReviewFollowUp n = new ReviewFollowUp();
                    n.setStudent(s);
                    n.setStatus("PLANNED");
                    return n;
                });
        if (req.getPlannedDate() != null) f.setPlannedDate(req.getPlannedDate());
        if (req.getOrgName() != null) f.setOrgName(req.getOrgName());

        if ("DONE".equals(req.getAction())) {
            f.setStatus("DONE");
            f.setActualDate(req.getActualDate() == null ? LocalDate.now() : req.getActualDate());
            f.setVisionResult(req.getVisionResult());
            f.setRemark(req.getRemark());
            followupRepo.save(f);
            timeline.event(s, EventType.REVIEW, "按期完成复查：" + nz(req.getVisionResult())
                    + (req.getRemark() == null ? "" : "（" + req.getRemark() + "）"));
            issueCloseIfOpen(s, ScreenFlag.REVIEW_NO_SHOW, "学生已补做复查");
            timeline.changeStage(s, StudentStage.CLOSED_LOOP);
            // 新一轮半年周期复查
            ReviewFollowUp next = new ReviewFollowUp();
            next.setStudent(s);
            next.setPlannedDate(f.getActualDate().plusMonths(6));
            next.setStatus("PLANNED");
            next.setOrgName(f.getOrgName());
            next.setRemark("下一周期半年复查");
            followupRepo.save(next);
        } else if ("NO_SHOW".equals(req.getAction())) {
            f.setStatus("NO_SHOW");
            f.setRemark(req.getRemark());
            f.setRemindCount((f.getRemindCount() == null ? 0 : f.getRemindCount()) + 1);
            followupRepo.save(f);
            timeline.raiseIssue(s, ScreenFlag.REVIEW_NO_SHOW,
                    "半年复查未到场（已催办 " + f.getRemindCount() + " 次）");
            timeline.event(s, EventType.REVIEW, "复查到期未到场，已发起第 " + f.getRemindCount() + " 次催办");
            timeline.changeStage(s, StudentStage.REVIEW_DUE);
        } else {
            followupRepo.save(f);
        }
        return f;
    }

    /* ================= 9. 学校日常干预 ================= */

    @Transactional
    public Intervention addIntervention(RecordDtos.InterventionRequest req) {
        Student s = requireStudent(req.getStudentId());
        InterventionType type;
        try {
            type = InterventionType.valueOf(req.getType());
        } catch (Exception e) {
            throw new BusinessException("干预类型不合法");
        }
        Intervention iv = new Intervention();
        iv.setStudent(s);
        iv.setType(type);
        iv.setExecDate(req.getExecDate() == null ? LocalDate.now() : req.getExecDate());
        iv.setExecutor(com.county.vision.security.SecurityUtils.actorLabel());
        iv.setContent(req.getContent());
        iv.setExecuted(!Boolean.FALSE.equals(req.getExecuted()));
        Intervention saved = interventionRepo.save(iv);
        timeline.event(s, EventType.INTERVENTION,
                "学校干预【" + type.getLabel() + "】" + (iv.getExecuted() ? "已执行" : "待执行")
                        + "：" + nz(req.getContent()));
        return saved;
    }

    /* ================= 异常事项闭环 ================= */

    @Transactional
    public IssueTracker resolveIssue(RecordDtos.IssueResolveRequest req) {
        return timeline.resolveIssue(req.getIssueId(), req.getResolveNote());
    }

    private void issueCloseIfOpen(Student s, ScreenFlag flag, String note) {
        List<IssueTracker> openIssues = issueRepo
                .findFirstByStudentIdAndIssueTypeAndStatus(s.getId(), flag.name(), "OPEN")
                .map(List::of).orElse(List.of());
        if (!openIssues.isEmpty()) {
            timeline.resolveIssue(openIssues.get(0).getId(), note);
        }
    }

    private String boolCn(Boolean b) {
        if (b == null) return "未填";
        return b ? "是" : "否";
    }

    private String money(BigDecimal b) {
        return b == null ? "—" : b.stripTrailingZeros().toPlainString() + " 元";
    }
}
