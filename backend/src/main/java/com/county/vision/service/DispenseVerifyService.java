package com.county.vision.service;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.*;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.VerifyDtos;
import com.county.vision.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合作门店配镜补助的发放核验：
 * 门店上传处方、镜片参数、付款凭证、取镜签收后，平台对照最近一次复诊处方、
 * 家庭资格与补助标准逐项核验，确认同一学生、同一副眼镜后才允许发放；
 * 处方不一致、参数缺失、付款凭证重复、门店重复提交的一律挂起，绝不重复发放。
 * 补助发放后的退镜/换片/换架按实际退款与新费用重算差额（追回或补发），
 * 学生不适反馈由门店上传调整结果，平台保留售后记录并继续提醒复查与适应回访。
 */
@Service
public class DispenseVerifyService {

    /** 补助标准：按实收合计的 50% 补助，单副上限 300 元 */
    public static final BigDecimal SUBSIDY_RATE = new BigDecimal("0.5");
    public static final BigDecimal SUBSIDY_CAP = new BigDecimal("300");

    private final StudentRepository studentRepo;
    private final GlassesRecordRepository glassesRepo;
    private final VisitRecordRepository visitRepo;
    private final StoreDispenseRepository dispenseRepo;
    private final SubsidyApplicationRepository subsidyRepo;
    private final AfterSalesRepository afterSalesRepo;
    private final ReviewFollowUpRepository followupRepo;
    private final IssueTrackerRepository issueRepo;
    private final TimelineService timeline;

    public DispenseVerifyService(StudentRepository studentRepo, GlassesRecordRepository glassesRepo,
                                 VisitRecordRepository visitRepo, StoreDispenseRepository dispenseRepo,
                                 SubsidyApplicationRepository subsidyRepo, AfterSalesRepository afterSalesRepo,
                                 ReviewFollowUpRepository followupRepo, IssueTrackerRepository issueRepo,
                                 TimelineService timeline) {
        this.studentRepo = studentRepo;
        this.glassesRepo = glassesRepo;
        this.visitRepo = visitRepo;
        this.dispenseRepo = dispenseRepo;
        this.subsidyRepo = subsidyRepo;
        this.afterSalesRepo = afterSalesRepo;
        this.followupRepo = followupRepo;
        this.issueRepo = issueRepo;
        this.timeline = timeline;
    }

    /* ================= 1. 门店上传材料 + 自动逐项核验 ================= */

    @Transactional
    public StoreDispense submit(VerifyDtos.DispenseSubmitRequest req) {
        Student s = studentRepo.findById(req.getStudentId())
                .orElseThrow(() -> new BusinessException("学生不存在"));
        GlassesRecord g = glassesRepo.findById(req.getGlassesId())
                .orElseThrow(() -> new BusinessException("配镜记录不存在"));
        StoreDispense d = new StoreDispense();
        d.setStudent(s);
        d.setGlasses(g);
        fill(d, req);
        StoreDispense saved = dispenseRepo.save(d);
        verify(saved);
        return saved;
    }

    /** 挂起后门店更正材料，重新提交核验 */
    @Transactional
    public StoreDispense resubmit(Long id, VerifyDtos.DispenseSubmitRequest req) {
        StoreDispense d = requireDispense(id);
        if (d.getStatus() != DispenseStatus.SUSPENDED) {
            throw new BusinessException("仅核验挂起的核验单可重新提交");
        }
        fill(d, req);
        verify(d);
        return d;
    }

    private void fill(StoreDispense d, VerifyDtos.DispenseSubmitRequest req) {
        d.setStoreName(req.getStoreName());
        d.setRxSphereLeft(req.getRxSphereLeft());
        d.setRxSphereRight(req.getRxSphereRight());
        d.setRxCylinderLeft(req.getRxCylinderLeft());
        d.setRxCylinderRight(req.getRxCylinderRight());
        d.setRxAxisLeft(req.getRxAxisLeft());
        d.setRxAxisRight(req.getRxAxisRight());
        d.setPd(req.getPd());
        d.setLensParams(req.getLensParams());
        d.setLensAmount(req.getLensAmount());
        d.setFrameAmount(req.getFrameAmount());
        BigDecimal total = req.getTotalAmount();
        if (total == null && req.getLensAmount() != null && req.getFrameAmount() != null) {
            total = req.getLensAmount().add(req.getFrameAmount());
        }
        d.setTotalAmount(total);
        d.setPaymentVoucherNo(req.getPaymentVoucherNo());
        d.setPickupSigned(Boolean.TRUE.equals(req.getPickupSigned()));
        d.setPickupDate(req.getPickupDate());
        d.setPickupSigner(req.getPickupSigner());
    }

    /** 逐项核验：同一学生 / 同一副眼镜 / 材料完整 / 处方一致 / 家庭资格 / 凭证唯一 / 补助标准 */
    private void verify(StoreDispense d) {
        Student s = d.getStudent();
        GlassesRecord g = d.getGlasses();
        List<String> detail = new ArrayList<>();
        List<String> fails = new ArrayList<>();

        // ① 同一学生：配镜记录必须归属该学生，且为合作门店配镜
        boolean sameStudent = g.getStudent().getId().equals(s.getId());
        check(detail, fails, sameStudent, "同一学生",
                "配镜记录#" + g.getId() + " 归属学生与核验学生一致",
                "配镜记录#" + g.getId() + " 不属于学生 " + s.getName() + "，非同一学生");
        if (!Boolean.TRUE.equals(g.getPartnerOptician())) {
            fails.add("该配镜记录为家长自行外配，不适用门店核验通道");
            detail.add("× 配镜渠道：家长自行外配，不适用门店核验");
        } else {
            detail.add("√ 配镜渠道：合作门店配镜");
        }

        // ② 同一副眼镜：同一配镜记录不得重复提交；已发放的不得再次发放
        List<StoreDispense> siblings = dispenseRepo.findByGlassesIdOrderByIdDesc(g.getId()).stream()
                .filter(x -> !x.getId().equals(d.getId()) && x.getStatus() != DispenseStatus.SUSPENDED)
                .toList();
        check(detail, fails, siblings.isEmpty(), "同一副眼镜",
                "该配镜记录无其他有效核验单",
                siblings.isEmpty() ? "" :
                        "门店重复提交：配镜记录#" + g.getId() + " 已存在核验单#" + siblings.get(0).getId()
                                + "（" + siblings.get(0).getStatus().getLabel() + "）");
        boolean alreadyPaid = subsidyRepo.existsByGlassesIdAndStatus(g.getId(), SubsidyStatus.PAID);
        check(detail, fails, !alreadyPaid, "重复发放拦截",
                "该配镜记录补助尚未发放",
                "该配镜记录补助已发放，禁止重复发放");

        // ③ 材料完整：镜片参数、费用、付款凭证、取镜签收缺一不可
        List<String> missing = new ArrayList<>();
        if (isBlank(d.getLensParams())) missing.add("镜片参数");
        if (d.getLensAmount() == null) missing.add("镜片费用");
        if (d.getTotalAmount() == null || d.getTotalAmount().signum() <= 0) missing.add("实收金额");
        if (isBlank(d.getPaymentVoucherNo())) missing.add("付款凭证号");
        if (!Boolean.TRUE.equals(d.getPickupSigned())) missing.add("取镜签收");
        check(detail, fails, missing.isEmpty(), "材料完整性",
                "处方、镜片参数、付款凭证、取镜签收齐全",
                "参数缺失：" + String.join("、", missing));

        // ④ 处方一致：对照最近一次复诊处方
        List<VisitRecord> visits = visitRepo.findByStudentIdOrderByVisitDateAscIdAsc(s.getId());
        VisitRecord v = visits.isEmpty() ? null : visits.get(visits.size() - 1);
        if (v == null) {
            fails.add("无最近一次复诊处方可对照");
            detail.add("× 处方一致性：学生尚无复诊处方");
        } else {
            List<String> mismatches = new ArrayList<>();
            rxField(mismatches, "球镜左", d.getRxSphereLeft(), v.getRxSphereLeft(), 0.011, true);
            rxField(mismatches, "球镜右", d.getRxSphereRight(), v.getRxSphereRight(), 0.011, true);
            rxField(mismatches, "柱镜左", d.getRxCylinderLeft(), v.getRxCylinderLeft(), 0.011, true);
            rxField(mismatches, "柱镜右", d.getRxCylinderRight(), v.getRxCylinderRight(), 0.011, true);
            rxField(mismatches, "轴位左", d.getRxAxisLeft(), v.getRxAxisLeft(), 5, false);
            rxField(mismatches, "轴位右", d.getRxAxisRight(), v.getRxAxisRight(), 5, false);
            rxField(mismatches, "瞳距", d.getPd(), v.getPd(), 1.0, false);
            check(detail, fails, mismatches.isEmpty(), "处方一致性",
                    "与 " + v.getVisitDate() + " 复诊处方逐项一致",
                    "处方不一致：" + String.join("；", mismatches)
                            + "（对照 " + v.getVisitDate() + " " + nz(v.getOrgName()) + " 复诊处方）");
        }

        // ⑤ 家庭资格：最近一次复诊医生初判符合补助
        boolean eligible = v != null && Boolean.TRUE.equals(v.getSubsidyEligible());
        check(detail, fails, eligible, "家庭资格",
                "最近一次复诊判定符合配镜补助",
                "家庭资格未确认：最近一次复诊未判定符合配镜补助");

        // ⑥ 付款凭证唯一：同一凭证号不得用于其他核验单
        if (!isBlank(d.getPaymentVoucherNo())) {
            List<StoreDispense> dup = dispenseRepo.findByPaymentVoucherNo(d.getPaymentVoucherNo()).stream()
                    .filter(x -> !x.getId().equals(d.getId())).toList();
            check(detail, fails, dup.isEmpty(), "付款凭证唯一",
                    "凭证号 " + d.getPaymentVoucherNo() + " 未被其他核验单使用",
                    dup.isEmpty() ? "" :
                            "付款凭证重复：凭证号 " + d.getPaymentVoucherNo()
                                    + " 已用于核验单#" + dup.get(0).getId()
                                    + "（学生：" + dup.get(0).getStudent().getName() + "）");
        }

        // ⑦ 补助标准：按实收合计 50%、上限 300 元核定
        BigDecimal standard = standardOf(d.getTotalAmount());
        d.setStandardAmount(standard);
        detail.add("√ 补助标准：实收 " + money(d.getTotalAmount()) + " × 50%，封顶 300 元，核定 " + money(standard));

        d.setVerifyDetail(String.join("\n", detail));
        d.setVerifiedBy("平台自动核验");
        d.setVerifiedAt(LocalDateTime.now());

        if (fails.isEmpty()) {
            d.setStatus(DispenseStatus.PASSED);
            d.setSuspendReason(null);
            linkSubsidy(d, standard);
            issueCloseIfOpen(s, ScreenFlag.DISPENSE_SUSPENDED, "门店已更正材料，重新核验通过");
            timeline.event(s, EventType.SUBSIDY,
                    "门店配镜核验通过（同一学生/同一副眼镜/处方/资格/凭证逐项对照一致），按补助标准核定 "
                            + money(standard) + "，待卫健部门确认发放");
        } else {
            d.setStatus(DispenseStatus.SUSPENDED);
            d.setSuspendReason(String.join("；", fails));
            timeline.raiseIssue(s, ScreenFlag.DISPENSE_SUSPENDED,
                    "门店配镜核验挂起：" + d.getSuspendReason());
            timeline.event(s, EventType.SUBSIDY,
                    "门店配镜核验挂起：" + d.getSuspendReason() + "，补助不予发放，待门店更正后重新核验");
        }
        dispenseRepo.save(d);
    }

    /** 核验通过后关联（或新建）补助申请，核准金额=标准核定金额 */
    private void linkSubsidy(StoreDispense d, BigDecimal standard) {
        SubsidyApplication app = d.getSubsidy();
        if (app == null) {
            app = subsidyRepo.findByGlassesIdOrderByIdDesc(d.getGlasses().getId()).stream()
                    .filter(a -> a.getStatus() != SubsidyStatus.REJECTED && a.getStatus() != SubsidyStatus.RECOVERED)
                    .findFirst().orElse(null);
        }
        if (app == null) {
            app = new SubsidyApplication();
            app.setStudent(d.getStudent());
            app.setGlasses(d.getGlasses());
            app.setApplyAmount(standard);
            app.setApplicant(d.getStoreName() + "（门店核验）");
            app.setApplyTime(LocalDateTime.now());
        }
        if (app.getStatus() != SubsidyStatus.PAID) {
            app.setStatus(SubsidyStatus.APPROVED);
            app.setApprovedAmount(standard);
            app.setApprover("平台自动核验");
            app.setApproveTime(LocalDateTime.now());
            app.setOpinion("门店核验通过，按补助标准（实收50%、封顶300元）核定");
        }
        d.setSubsidy(subsidyRepo.save(app));
    }

    /* ================= 2. 卫健确认发放（防二次发放） ================= */

    @Transactional
    public SubsidyApplication pay(Long dispenseId) {
        StoreDispense d = requireDispense(dispenseId);
        if (d.getStatus() != DispenseStatus.PASSED) {
            throw new BusinessException("仅核验通过的核验单可发放补助（当前：" + d.getStatus().getLabel() + "）");
        }
        SubsidyApplication app = d.getSubsidy();
        if (app == null) {
            throw new BusinessException("核验单未关联补助申请");
        }
        if (app.getStatus() == SubsidyStatus.PAID) {
            throw new BusinessException("该补助已发放，禁止重复发放");
        }
        if (app.getStatus() != SubsidyStatus.APPROVED) {
            throw new BusinessException("补助当前状态（" + app.getStatus().getLabel() + "）不可发放");
        }
        if (subsidyRepo.existsByGlassesIdAndStatus(d.getGlasses().getId(), SubsidyStatus.PAID)) {
            throw new BusinessException("该配镜记录已发放过补助，禁止重复发放");
        }
        app.setStatus(SubsidyStatus.PAID);
        app.setApprover(SecurityUtils.actorLabel());
        app.setApproveTime(LocalDateTime.now());
        subsidyRepo.save(app);
        timeline.event(d.getStudent(), EventType.SUBSIDY,
                "配镜补助已发放 " + money(app.getApprovedAmount()) + "（门店核验单#" + d.getId() + "）");
        return app;
    }

    /* ================= 3. 售后：退镜/换片/换架差额重算 ================= */

    @Transactional
    public AfterSales registerAfterSales(VerifyDtos.AfterSalesRequest req) {
        StoreDispense d = requireDispense(req.getDispenseId());
        if (d.getStatus() != DispenseStatus.PASSED) {
            throw new BusinessException("仅核验通过并已发放的配镜可登记售后");
        }
        SubsidyApplication app = d.getSubsidy();
        if (app == null || app.getStatus() != SubsidyStatus.PAID) {
            throw new BusinessException("该配镜补助未发放，无需售后差额处理");
        }
        AfterSalesType type;
        try {
            type = AfterSalesType.valueOf(req.getType());
        } catch (Exception e) {
            throw new BusinessException("售后类型只能为 RETURN/LENS_CHANGE/FRAME_CHANGE");
        }
        if (type == AfterSalesType.ADJUST) {
            throw new BusinessException("不适调整请走不适反馈登记");
        }

        BigDecimal old = app.getApprovedAmount() == null ? BigDecimal.ZERO : app.getApprovedAmount();
        BigDecimal newSubsidy;
        BigDecimal newAmount;
        if (type == AfterSalesType.RETURN) {
            newAmount = BigDecimal.ZERO;
            newSubsidy = BigDecimal.ZERO;
        } else {
            if (req.getNewAmount() == null || req.getNewAmount().signum() < 0) {
                throw new BusinessException("换片/换架必须填写售后后的新费用合计");
            }
            newAmount = req.getNewAmount();
            newSubsidy = standardOf(newAmount);
        }
        BigDecimal difference = newSubsidy.subtract(old);

        AfterSales as = new AfterSales();
        as.setStudent(d.getStudent());
        as.setDispense(d);
        as.setSubsidy(app);
        as.setType(type);
        as.setHandleDate(LocalDate.now());
        as.setReason(req.getReason());
        as.setRefundAmount(req.getRefundAmount());
        as.setNewAmount(newAmount);
        as.setOldSubsidy(old);
        as.setNewSubsidy(newSubsidy);
        as.setDifference(difference);
        as.setStatus("PENDING_SETTLE");
        as.setHandler(SecurityUtils.actorLabel());
        AfterSales saved = afterSalesRepo.save(as);

        String diffText = difference.signum() > 0 ? "应补发 " + money(difference)
                : difference.signum() < 0 ? "应追回 " + money(difference.negate()) : "无差额";
        timeline.raiseIssue(d.getStudent(), ScreenFlag.SUBSIDY_SETTLE,
                "配镜售后【" + type.getLabel() + "】" + diffText + "，待卫健结算");
        timeline.event(d.getStudent(), EventType.SUBSIDY,
                "售后登记【" + type.getLabel() + "】：实际退款 " + money(req.getRefundAmount())
                        + "，新费用 " + money(newAmount) + "，按补助标准重算应补 " + money(newSubsidy)
                        + "（原补助 " + money(old) + "），" + diffText + "，售后单#" + saved.getId() + " 与原补助关联待结算");
        return saved;
    }

    /** 卫健确认结算：追回或补发差额，并更新原补助 */
    @Transactional
    public AfterSales settle(Long afterSalesId) {
        AfterSales as = afterSalesRepo.findById(afterSalesId)
                .orElseThrow(() -> new BusinessException("售后记录不存在"));
        if (!"PENDING_SETTLE".equals(as.getStatus())) {
            throw new BusinessException("该售后记录不在待结算状态");
        }
        SubsidyApplication app = as.getSubsidy();
        as.setStatus("SETTLED");
        as.setHandler(SecurityUtils.actorLabel());
        afterSalesRepo.save(as);

        app.setApprovedAmount(as.getNewSubsidy());
        String result;
        if (as.getNewSubsidy().signum() == 0) {
            app.setStatus(SubsidyStatus.RECOVERED);
            result = "已全额追回 " + money(as.getOldSubsidy()) + "，原补助标记为已追回";
        } else if (as.getDifference().signum() < 0) {
            result = "已追回差额 " + money(as.getDifference().negate()) + "，补助现额 " + money(as.getNewSubsidy());
        } else if (as.getDifference().signum() > 0) {
            result = "已补发差额 " + money(as.getDifference()) + "，补助现额 " + money(as.getNewSubsidy());
        } else {
            result = "无差额，补助维持 " + money(as.getNewSubsidy());
        }
        app.setOpinion((app.getOpinion() == null ? "" : app.getOpinion() + "；")
                + "售后【" + as.getType().getLabel() + "】结算：" + result);
        subsidyRepo.save(app);

        issueCloseIfOpen(as.getStudent(), ScreenFlag.SUBSIDY_SETTLE, "售后差额已结算：" + result);
        timeline.event(as.getStudent(), EventType.SUBSIDY,
                "售后结算完成【" + as.getType().getLabel() + "】：" + result + "（关联原补助申请#" + app.getId() + "）");
        return as;
    }

    /* ================= 4. 不适反馈与门店调整 ================= */

    /** 学生反馈头晕/看不清/镜架不合适，登记售后并通知门店调整 */
    @Transactional
    public AfterSales feedback(VerifyDtos.FeedbackRequest req) {
        StoreDispense d = requireDispense(req.getDispenseId());
        if (isBlank(req.getFeedback())) {
            throw new BusinessException("请填写学生不适反馈内容");
        }
        AfterSales as = new AfterSales();
        as.setStudent(d.getStudent());
        as.setDispense(d);
        as.setSubsidy(d.getSubsidy());
        as.setType(AfterSalesType.ADJUST);
        as.setHandleDate(LocalDate.now());
        as.setReason(req.getFeedback());
        as.setStatus("WAIT_ADJUST");
        as.setHandler(SecurityUtils.actorLabel());
        AfterSales saved = afterSalesRepo.save(as);

        timeline.raiseIssue(d.getStudent(), ScreenFlag.GLASSES_DISCOMFORT,
                "学生配镜后反馈：" + req.getFeedback() + "，待门店上传调整结果");
        timeline.event(d.getStudent(), EventType.GLASSES,
                "学生反馈佩戴不适：" + req.getFeedback() + "，已通知 " + nz(d.getStoreName()) + " 调整（售后单#" + saved.getId() + "）");
        return saved;
    }

    /** 门店上传调整结果：保留售后记录，并继续提醒复查与适应回访 */
    @Transactional
    public AfterSales uploadAdjustResult(Long afterSalesId, String adjustResult) {
        AfterSales as = afterSalesRepo.findById(afterSalesId)
                .orElseThrow(() -> new BusinessException("售后记录不存在"));
        if (as.getType() != AfterSalesType.ADJUST || !"WAIT_ADJUST".equals(as.getStatus())) {
            throw new BusinessException("该售后记录不在待门店调整状态");
        }
        if (isBlank(adjustResult)) {
            throw new BusinessException("请填写门店调整结果");
        }
        as.setAdjustResult(adjustResult);
        as.setStatus("ADJUSTED");
        as.setHandler(SecurityUtils.actorLabel());
        afterSalesRepo.save(as);

        Student s = as.getStudent();
        issueCloseIfOpen(s, ScreenFlag.GLASSES_DISCOMFORT, "门店已上传调整结果：" + adjustResult);
        timeline.event(s, EventType.GLASSES, "门店已上传调整结果：" + adjustResult + "，平台保留售后记录");

        // 适应回访提醒（14 天后）
        StoreDispense d = as.getDispense();
        ReviewFollowUp adapt = new ReviewFollowUp();
        adapt.setStudent(s);
        adapt.setKind("ADAPT");
        adapt.setPlannedDate(LocalDate.now().plusDays(14));
        adapt.setStatus("PLANNED");
        adapt.setOrgName(d == null ? null : d.getStoreName());
        adapt.setRemark("配镜不适调整后的适应回访（原反馈：" + as.getReason() + "）");
        followupRepo.save(adapt);

        // 继续提醒周期复查：若已有计划中的复查，提示其计划日期
        String reviewNote = followupRepo.findByStudentIdOrderByPlannedDateAscIdAsc(s.getId()).stream()
                .filter(f -> "REVIEW".equals(f.getKind() == null ? "REVIEW" : f.getKind()))
                .filter(f -> "PLANNED".equals(f.getStatus()) || "NO_SHOW".equals(f.getStatus()))
                .findFirst()
                .map(f -> "；周期复查计划 " + f.getPlannedDate() + " 继续提醒")
                .orElse("；暂无进行中的周期复查计划");
        timeline.event(s, EventType.REVIEW,
                "已建立适应回访提醒，应于 " + adapt.getPlannedDate() + " 前完成回访" + reviewNote);
        return as;
    }

    /* ================= 查询与投影 ================= */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> dispenses(String status) {
        List<StoreDispense> list;
        if (status == null || status.isBlank() || "ALL".equals(status)) {
            list = dispenseRepo.findAll();
        } else {
            try {
                list = dispenseRepo.findByStatusOrderByIdDesc(DispenseStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                list = dispenseRepo.findAll();
            }
        }
        return list.stream().map(this::dispenseMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> afterSalesList(String status) {
        List<AfterSales> list = (status == null || status.isBlank() || "ALL".equals(status))
                ? afterSalesRepo.findAll() : afterSalesRepo.findByStatusOrderByIdDesc(status);
        return list.stream().map(this::afterSalesMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentDispenses(Long studentId) {
        return dispenseRepo.findByStudentIdOrderByIdDesc(studentId).stream().map(this::dispenseMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentAfterSales(Long studentId) {
        return afterSalesRepo.findByStudentIdOrderByIdDesc(studentId).stream().map(this::afterSalesMap).toList();
    }

    public Map<String, Object> dispenseMap(StoreDispense d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("studentId", d.getStudent().getId());
        m.put("studentName", d.getStudent().getName());
        m.put("schoolName", d.getStudent().getSchool().getName());
        m.put("glassesId", d.getGlasses().getId());
        m.put("storeName", d.getStoreName());
        m.put("rxSphereLeft", d.getRxSphereLeft());
        m.put("rxSphereRight", d.getRxSphereRight());
        m.put("rxCylinderLeft", d.getRxCylinderLeft());
        m.put("rxCylinderRight", d.getRxCylinderRight());
        m.put("rxAxisLeft", d.getRxAxisLeft());
        m.put("rxAxisRight", d.getRxAxisRight());
        m.put("pd", d.getPd());
        m.put("lensParams", d.getLensParams());
        m.put("lensAmount", d.getLensAmount());
        m.put("frameAmount", d.getFrameAmount());
        m.put("totalAmount", d.getTotalAmount());
        m.put("paymentVoucherNo", d.getPaymentVoucherNo());
        m.put("pickupSigned", d.getPickupSigned());
        m.put("pickupDate", d.getPickupDate());
        m.put("pickupSigner", d.getPickupSigner());
        m.put("status", d.getStatus().name());
        m.put("statusLabel", d.getStatus().getLabel());
        m.put("verifyDetail", d.getVerifyDetail());
        m.put("suspendReason", d.getSuspendReason());
        m.put("verifiedBy", d.getVerifiedBy());
        m.put("verifiedAt", d.getVerifiedAt());
        m.put("standardAmount", d.getStandardAmount());
        m.put("subsidyId", d.getSubsidy() == null ? null : d.getSubsidy().getId());
        m.put("subsidyStatus", d.getSubsidy() == null ? null : d.getSubsidy().getStatus().name());
        m.put("subsidyStatusLabel", d.getSubsidy() == null ? null : d.getSubsidy().getStatus().getLabel());
        m.put("approvedAmount", d.getSubsidy() == null ? null : d.getSubsidy().getApprovedAmount());
        return m;
    }

    public Map<String, Object> afterSalesMap(AfterSales as) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", as.getId());
        m.put("studentId", as.getStudent().getId());
        m.put("studentName", as.getStudent().getName());
        m.put("schoolName", as.getStudent().getSchool().getName());
        m.put("dispenseId", as.getDispense().getId());
        m.put("storeName", as.getDispense().getStoreName());
        m.put("subsidyId", as.getSubsidy() == null ? null : as.getSubsidy().getId());
        m.put("type", as.getType().name());
        m.put("typeLabel", as.getType().getLabel());
        m.put("handleDate", as.getHandleDate());
        m.put("reason", as.getReason());
        m.put("refundAmount", as.getRefundAmount());
        m.put("newAmount", as.getNewAmount());
        m.put("oldSubsidy", as.getOldSubsidy());
        m.put("newSubsidy", as.getNewSubsidy());
        m.put("difference", as.getDifference());
        m.put("adjustResult", as.getAdjustResult());
        m.put("status", as.getStatus());
        m.put("statusLabel", switch (as.getStatus()) {
            case "PENDING_SETTLE" -> "待结算";
            case "SETTLED" -> "已结算";
            case "WAIT_ADJUST" -> "待门店调整";
            case "ADJUSTED" -> "已调整（适应回访中）";
            default -> as.getStatus();
        });
        m.put("handler", as.getHandler());
        return m;
    }

    /* ================= 工具 ================= */

    /** 补助标准：实收合计 × 50%，封顶 300 元 */
    public static BigDecimal standardOf(BigDecimal total) {
        if (total == null || total.signum() <= 0) return BigDecimal.ZERO;
        return total.multiply(SUBSIDY_RATE).setScale(2, RoundingMode.HALF_UP).min(SUBSIDY_CAP);
    }

    private StoreDispense requireDispense(Long id) {
        return dispenseRepo.findById(id).orElseThrow(() -> new BusinessException("核验单不存在"));
    }

    private void check(List<String> detail, List<String> fails, boolean ok,
                       String item, String okText, String failText) {
        if (ok) {
            detail.add("√ " + item + "：" + okText);
        } else {
            detail.add("× " + item + "：" + failText);
            fails.add(failText);
        }
    }

    private void rxField(List<String> mismatches, String label, String storeVal, String visitVal,
                         double tolerance, boolean nullAsZero) {
        Double a = parseNum(storeVal);
        Double b = parseNum(visitVal);
        if (nullAsZero) {
            if (a == null) a = 0.0;
            if (b == null) b = 0.0;
        }
        if (a == null && b == null) return;
        if (a == null || b == null || Math.abs(a - b) > tolerance) {
            mismatches.add(label + " 门店" + nz(storeVal) + " vs 复诊" + nz(visitVal));
        }
    }

    private Double parseNum(String s) {
        if (isBlank(s)) return null;
        try {
            return Double.parseDouble(s.replace("D", "").replace("度", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void issueCloseIfOpen(Student s, ScreenFlag flag, String note) {
        issueRepo.findFirstByStudentIdAndIssueTypeAndStatus(s.getId(), flag.name(), "OPEN")
                .ifPresent(i -> timeline.resolveIssue(i.getId(), note));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String nz(String s) {
        return isBlank(s) ? "—" : s;
    }

    private String money(BigDecimal b) {
        return b == null ? "—" : b.stripTrailingZeros().toPlainString() + " 元";
    }
}
