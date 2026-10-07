package com.county.vision;

import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.*;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.RecordDtos;
import com.county.vision.dto.VerificationDtos;
import com.county.vision.service.FlowService;
import com.county.vision.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 合作门店配镜补助发放核验规则集成测试：
 * 七项逐项核验通过→发放；处方不一致/参数缺失/凭证重复/门店重复提交挂起；不重复发两次；
 * 售后退镜追回、换片补发差额与原补助连档。
 */
@SpringBootTest
@ActiveProfiles("test")
class VerificationServiceTests {

    @Autowired private VerificationService verification;
    @Autowired private FlowService flow;
    @Autowired private StudentRepository studentRepo;
    @Autowired private SchoolRepository schoolRepo;
    @Autowired private RegionRepository regionRepo;
    @Autowired private GradeClassRepository classRepo;
    @Autowired private SubsidyVerificationRepository verificationRepo;
    @Autowired private AfterSalesRepository afterSalesRepo;

    private Region region;
    private School school;
    private GradeClass gc;

    @BeforeEach
    void setup() {
        region = new Region();
        region.setName("测试片区");
        region.setCode("T");
        regionRepo.save(region);
        school = new School();
        school.setName("测试小学");
        school.setRegion(region);
        schoolRepo.save(school);
        gc = new GradeClass();
        gc.setSchool(school);
        gc.setGrade("三年级");
        gc.setClassName("1班");
        classRepo.save(gc);
    }

    private Student readyStudent(String no, String parent) {
        Student s = new Student();
        s.setSchool(school);
        s.setGradeClass(gc);
        s.setStudentNo(no);
        s.setName(no);
        s.setGender("男");
        s.setParentName(parent);
        s.setParentPhone("13800000000");
        s.setScreenYear(2026);
        studentRepo.save(s);

        RecordDtos.ScreenRequest scr = new RecordDtos.ScreenRequest();
        scr.setStudentId(s.getId());
        scr.setScreenDate(LocalDate.of(2026, 10, 1));
        scr.setNakedVisionLeft("4.6");
        scr.setNakedVisionRight("4.6");
        scr.setSphereLeft("-1.50");
        scr.setSphereRight("-1.50");
        flow.submitScreen(scr);

        RecordDtos.SchoolReviewRequest rv = new RecordDtos.SchoolReviewRequest();
        rv.setStudentId(s.getId());
        rv.setResult("CONFIRM");
        flow.schoolReview(rv);

        RecordDtos.NotifyRequest nt = new RecordDtos.NotifyRequest();
        nt.setStudentId(s.getId());
        nt.setChannel("短信");
        flow.notifyParent(nt);

        RecordDtos.ParentChoiceRequest pc = new RecordDtos.ParentChoiceRequest();
        pc.setStudentId(s.getId());
        pc.setChoice("PARTNER_CLINIC");
        pc.setTargetOrg("测试门店");
        flow.parentChoice(pc);

        RecordDtos.VisitRequest v = new RecordDtos.VisitRequest();
        v.setStudentId(s.getId());
        v.setOrgName("测试门店");
        v.setVisitDate(LocalDate.of(2026, 10, 5));
        v.setTrueMyopia(true);
        v.setRxSphereLeft("-1.50");
        v.setRxSphereRight("-1.50");
        v.setPd("60");
        v.setSubsidyEligible(true);
        v.setReviewCycleMonths(6);
        flow.submitVisit(v);
        return s;
    }

    private VerificationDtos.StoreGlassesRequest storeReq(Long sid, String parent, String voucher) {
        VerificationDtos.StoreGlassesRequest r = new VerificationDtos.StoreGlassesRequest();
        r.setStudentId(sid);
        r.setOpticianOrg("测试门店");
        r.setGlassesDate(LocalDate.of(2026, 10, 8));
        r.setAmount(new BigDecimal("680.00"));
        r.setLensBrand("1.56镜片");
        r.setRxNo("RX-" + sid);
        r.setRxSphereLeft("-1.50");
        r.setRxSphereRight("-1.50");
        r.setRxPd("60");
        r.setLensSphereLeft("-1.50");
        r.setLensSphereRight("-1.50");
        r.setLensPd("60");
        r.setPaymentVoucherNo(voucher);
        r.setPickedUp(true);
        r.setPickupDate(LocalDate.of(2026, 10, 8));
        r.setReceiverName(parent);
        return r;
    }

    @Test
    void allChecksPass_thenPayOnce_andBlockSecondPay() {
        Student s = readyStudent("S1", "S1家长");
        SubsidyVerification v = verification.submitStoreGlasses(storeReq(s.getId(), "S1家长", "V-001"));
        assertEquals(VerificationStatus.PASSED, v.getStatus());
        assertEquals(CheckResult.PASS, v.getStudentCheck());
        assertEquals(CheckResult.PASS, v.getPrescriptionCheck());
        assertEquals(CheckResult.PASS, v.getLensParamCheck());
        assertEquals(CheckResult.PASS, v.getEligibilityCheck());
        assertEquals(CheckResult.PASS, v.getVoucherCheck());
        assertEquals(CheckResult.PASS, v.getPickupCheck());
        assertEquals(CheckResult.PASS, v.getStandardCheck());
        assertEquals(0, new BigDecimal("300.00").compareTo(v.getPayableAmount()));

        verification.payVerification(v.getId());
        assertEquals(VerificationStatus.PAID, verificationRepo.findById(v.getId()).orElseThrow().getStatus());

        // 同一学生再次提交 → 重复提交挂起，不能发第二次
        SubsidyVerification again = verification.submitStoreGlasses(storeReq(s.getId(), "S1家长", "V-002"));
        assertEquals(VerificationStatus.SUSPENDED, again.getStatus());
        assertTrue(again.getHoldReasons().contains("门店重复提交"));
    }

    @Test
    void prescriptionMismatch_isSuspended() {
        Student s = readyStudent("S2", "S2家长");
        VerificationDtos.StoreGlassesRequest req = storeReq(s.getId(), "S2家长", "V-003");
        req.setRxSphereLeft("-0.50"); // 与复诊处方 -1.50 不一致
        req.setLensSphereLeft("-0.50");
        SubsidyVerification v = verification.submitStoreGlasses(req);
        assertEquals(VerificationStatus.SUSPENDED, v.getStatus());
        assertEquals(CheckResult.FAIL, v.getPrescriptionCheck());
        assertTrue(v.getHoldReasons().contains("处方不一致"));
    }

    @Test
    void missingLensParam_isSuspended() {
        Student s = readyStudent("S3", "S3家长");
        VerificationDtos.StoreGlassesRequest req = storeReq(s.getId(), "S3家长", "V-004");
        req.setLensPd(null); // 加工瞳距缺失
        SubsidyVerification v = verification.submitStoreGlasses(req);
        assertEquals(VerificationStatus.SUSPENDED, v.getStatus());
        assertEquals(CheckResult.MISSING, v.getLensParamCheck());
    }

    @Test
    void duplicateVoucher_isSuspended() {
        Student a = readyStudent("S4", "S4家长");
        Student b = readyStudent("S5", "S5家长");
        verification.payVerification(verification.submitStoreGlasses(storeReq(a.getId(), "S4家长", "DUP-V")).getId());
        SubsidyVerification v = verification.submitStoreGlasses(storeReq(b.getId(), "S5家长", "DUP-V"));
        assertEquals(VerificationStatus.SUSPENDED, v.getStatus());
        assertEquals(CheckResult.FAIL, v.getVoucherCheck());
        assertTrue(v.getHoldReasons().contains("付款凭证重复"));
    }

    @Test
    void refundAfterPay_recoversFullSubsidy() {
        Student s = readyStudent("S6", "S6家长");
        SubsidyVerification v = verification.payVerification(
                verification.submitStoreGlasses(storeReq(s.getId(), "S6家长", "V-006")).getId());
        assertEquals(0, new BigDecimal("300").compareTo(v.getPaidAmount()));

        VerificationDtos.AfterSalesRequest req = new VerificationDtos.AfterSalesRequest();
        req.setGlassesId(v.getGlasses().getId());
        req.setType("REFUND");
        req.setRefundAmount(new BigDecimal("680.00"));
        req.setNewCostAmount(BigDecimal.ZERO);
        AfterSales a = verification.registerAfterSales(req);
        assertEquals(SettleDirection.RECOVER, a.getDirection());
        assertEquals(0, new BigDecimal("300").compareTo(a.getSettleAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(a.getRecalculatedSubsidy()));
        assertEquals("REQUESTED", a.getStatus());
        assertEquals(1, afterSalesRepo.findByStatusOrderByIdDesc("REQUESTED").size());

        VerificationDtos.AfterSalesSettleRequest st = new VerificationDtos.AfterSalesSettleRequest();
        st.setAfterSalesId(a.getId());
        st.setSettleVoucherNo("RC-1");
        verification.settleAfterSales(st);
        assertEquals(VerificationStatus.CLOSED, verificationRepo.findById(v.getId()).orElseThrow().getStatus());
    }

    @Test
    void lensReplaceWithHigherCost_supplementsDifference() {
        Student s = readyStudent("S7", "S7家长");
        VerificationDtos.StoreGlassesRequest req = storeReq(s.getId(), "S7家长", "V-007");
        req.setAmount(new BigDecimal("200.00")); // 原费用低于上限，原补助 200
        SubsidyVerification v = verification.payVerification(verification.submitStoreGlasses(req).getId());
        assertEquals(0, new BigDecimal("200").compareTo(v.getPaidAmount()));

        VerificationDtos.AfterSalesRequest as = new VerificationDtos.AfterSalesRequest();
        as.setGlassesId(v.getGlasses().getId());
        as.setType("LENS_REPLACE");
        as.setRefundAmount(new BigDecimal("50.00"));
        as.setNewCostAmount(new BigDecimal("400.00"));
        as.setSettledNow(true);
        AfterSales saved = verification.registerAfterSales(as);
        // 结算基数 200-50+400=550 → 重算补助 300，应补发 100
        assertEquals(SettleDirection.SUPPLEMENT, saved.getDirection());
        assertEquals(0, new BigDecimal("100").compareTo(saved.getSettleAmount()));
        assertEquals("SETTLED", saved.getStatus());
        assertEquals(VerificationStatus.CLOSED, verificationRepo.findById(v.getId()).orElseThrow().getStatus());
    }
}
