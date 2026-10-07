package com.county.vision.seed;

import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.Role;
import com.county.vision.domain.enums.ScreenFlag;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.RecordDtos;
import com.county.vision.dto.VerifyDtos;
import com.county.vision.service.DispenseVerifyService;
import com.county.vision.service.FlowService;
import com.county.vision.service.TimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 演示种子数据：
 * 1) 片区/学校/各类角色账号；
 * 2) 覆盖全部异常场景：请假、电话不通、导入错误、复诊差异大、外配眼镜、
 *    补助争议、配镜不适、半年复查未到场，以及完整闭环、暂缓、正常等对照样例。
 */
@Slf4j
@Component
@Profile("!test")
public class DataSeeder implements ApplicationRunner {

    private final UserAccountRepository userRepo;
    private final RegionRepository regionRepo;
    private final SchoolRepository schoolRepo;
    private final GradeClassRepository classRepo;
    private final StudentRepository studentRepo;
    private final IssueTrackerRepository issueRepo;
    private final PasswordEncoder encoder;
    private final FlowService flow;
    private final TimelineService timeline;
    private final DispenseVerifyService verify;

    public DataSeeder(UserAccountRepository userRepo, RegionRepository regionRepo,
                      SchoolRepository schoolRepo, GradeClassRepository classRepo,
                      StudentRepository studentRepo, IssueTrackerRepository issueRepo,
                      PasswordEncoder encoder,
                      FlowService flow, TimelineService timeline, DispenseVerifyService verify) {
        this.userRepo = userRepo;
        this.regionRepo = regionRepo;
        this.schoolRepo = schoolRepo;
        this.classRepo = classRepo;
        this.studentRepo = studentRepo;
        this.issueRepo = issueRepo;
        this.encoder = encoder;
        this.flow = flow;
        this.timeline = timeline;
        this.verify = verify;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepo.count() > 0) return;

        Region city = region("城区片区", "R01");
        Region east = region("东乡片区", "R02");
        Region west = region("西乡片区", "R03");

        School s1 = school("阳光县第一小学", city, "健康老师", "李校医");
        School s2 = school("阳光县东乡中心小学", east, "王校医", "王校医");
        School s3 = school("阳光县实验中学", city, "赵校医", "赵校医");
        School s4 = school("阳光县西乡中学", west, "孙校医", "孙校医");

        String pwd = encoder.encode("123456");
        account("admin", pwd, "平台管理员", Role.ADMIN, null, "平台管理处", null);
        account("edu", pwd, "教体局-督导科", Role.GOV_EDU, null, "县教体局", "0551-88880001");
        account("health", pwd, "卫健委-基层卫生科", Role.GOV_HEALTH, null, "县卫健委", "0551-88880002");
        account("teacher1", pwd, "三班班主任 陈老师", Role.TEACHER, s1, null, "13900000001");
        account("doctor1", pwd, "一小健康老师 李校医", Role.SCHOOL_DOCTOR, s1, null, "13900000002");
        account("teacher2", pwd, "东乡小学班主任 周老师", Role.TEACHER, s2, null, "13900000003");
        account("screener", pwd, "县视光中心-筛查队", Role.SCREEN_ORG, null, "阳光县视光中心", "13900000004");
        account("hospital", pwd, "县人民医院-眼科", Role.HOSPITAL, null, "阳光县人民医院眼科", "13900000005");
        account("clinic", pwd, "爱眼合作眼科门诊", Role.CLINIC, null, "爱眼眼科门诊", "13900000006");
        account("store", pwd, "阳光眼镜合作门店-验配部", Role.CLINIC, null, "阳光眼镜合作门店", "13900000007");
        account("parent", pwd, "张爸爸", Role.PARENT, null, null, "13800001111");

        int year = LocalDate.now().getYear();

        // ---- 1. 完整闭环学生（张明，三年级2班） ----
        Student zhang = student(s1, grade(s1, "三年级", "2班"), "2024030201", "张明", "男",
                LocalDate.of(2014, 3, 2), "张爸爸", "13800001111", "2025年筛查裸眼视力4.9", year);
        flow.submitScreen(screen(zhang.getId(), "4.6", "4.7", "-1.50", "-1.25", "-0.50", "-0.25",
                false, "每日近距离用眼约3小时，户外活动不足1小时", false, null, d(2026, 9, 15)));
        flow.schoolReview(review(zhang.getId(), "CONFIRM", "裸眼视力下降明显，档案与既往一致，确认异常"));
        flow.notifyParent(notify(zhang.getId(), "短信+APP", false, null));
        flow.parentChoice(choice(zhang.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(zhang.getId(), "阳光县人民医院眼科", "刘医生", d(2026, 9, 22),
                true, true, "-1.75", "-1.50", "-0.50", "-0.25", "180", "175", "60",
                "双眼真性近视，建议配镜并3个月复查", 3, true, false, null));
        flow.submitGlasses(glasses(zhang.getId(), "县人民医院配镜中心", d(2026, 9, 30),
                true, new BigDecimal("680.00"), "1.56防蓝光镜片 球镜-1.75/-1.50", false, null));
        Long app1 = flow.applySubsidy(subsidy(zhang.getId(), new BigDecimal("300.00"), "真性近视，符合县域补助")).getId();
        flow.approveSubsidy(approve(app1, "APPROVED", new BigDecimal("300.00"), "材料齐全"));
        flow.approveSubsidy(approve(app1, "PAID", new BigDecimal("300.00"), "补助已发放至家长账户"));
        flow.addIntervention(intervention(zhang.getId(), "SEAT_ADJUST", d(2026, 9, 25), "调整至教室前排正中座位"));
        flow.addIntervention(intervention(zhang.getId(), "OUTDOOR_ACTIVITY", d(2026, 9, 26), "每天保证2小时户外活动"));
        flow.addIntervention(intervention(zhang.getId(), "PARENT_REMIND", d(2026, 10, 1), "提醒控制电子产品使用并监督配镜佩戴"));
        flow.followUp(followup(zhang.getId(), null, d(2026, 12, 22), "DONE",
                "阳光县人民医院眼科", "双眼矫正视力5.0，度数稳定", "3个月复查正常"));

        // ---- 2. 合作门诊复诊、配镜后持续管理（李华，四年级1班） ----
        Student li = student(s1, grade(s1, "四年级", "1班"), "2024040102", "李华", "女",
                LocalDate.of(2013, 6, 11), "李妈妈", "13800002222", "既往无异常", year);
        flow.submitScreen(screen(li.getId(), "4.7", "4.8", "-1.00", "-0.75", null, null,
                false, "读写姿势不规范", false, null, d(2026, 9, 16)));
        flow.schoolReview(review(li.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(li.getId(), "电话", false, null));
        flow.parentChoice(choice(li.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(li.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 25),
                true, false, "-1.00", "-0.75", null, null, null, null, "58",
                "双眼低度近视", 6, true, false, null));
        flow.submitGlasses(glasses(li.getId(), "爱眼门诊配镜部", d(2026, 10, 2),
                true, new BigDecimal("560.00"), "1.56树脂片", false, null));
        flow.addIntervention(intervention(li.getId(), "OUTDOOR_ACTIVITY", d(2026, 10, 3), "增加课间远眺与户外活动"));

        // ---- 3. 筛查请假（王芳，三年级2班）—— ABSENT 待补检 ----
        Student wang = student(s1, grade(s1, "三年级", "2班"), "2024030203", "王芳", "女",
                LocalDate.of(2014, 8, 19), "王爸爸", "13800003333", "2025年视力4.8", year);
        RecordDtos.ScreenRequest absentReq = new RecordDtos.ScreenRequest();
        absentReq.setStudentId(wang.getId());
        absentReq.setAbsent(true);
        absentReq.setScreenDate(d(2026, 9, 15));
        absentReq.setOrgName("阳光县视光中心");
        absentReq.setOperatorName("筛查队-小马");
        absentReq.setDeviceCalibNo("DEV-2026-0091");
        flow.submitScreen(absentReq);

        // ---- 4. 家长电话无法接通（赵强，四年级1班）----
        Student zhao = student(s1, grade(s1, "四年级", "1班"), "2024040104", "赵强", "男",
                LocalDate.of(2013, 11, 2), "赵爷爷", "13800004444", null, year);
        flow.submitScreen(screen(zhao.getId(), "4.5", "4.6", "-2.00", "-1.75", "-0.75", "-0.50",
                false, "电子产品每日4小时", false, null, d(2026, 9, 16)));
        flow.schoolReview(review(zhao.getId(), "CONFIRM", "确认异常，需尽快告知家长"));
        flow.notifyParent(notify(zhao.getId(), "电话", true, null));
        flow.notifyParent(notify(zhao.getId(), "电话", true, "连续两天拨打无人接听"));

        // ---- 5. 批量导入信息错误（陈静，三年级3班，手机号位数不对）----
        GradeClass gc33 = grade(s1, "三年级", "3班");
        Student chen = student(s1, gc33, "2024030305", "陈静", "女",
                LocalDate.of(2014, 1, 25), "陈妈妈", "138-0005", "既往无异常", year);
        chen.setImportError(true);
        chen.setImportErrorDetail("家长电话格式不正确（应为11位手机号）；缺性别");
        chen.setGender(null);
        studentRepo.save(chen);
        timeline.raiseIssue(chen, ScreenFlag.IMPORT_ERROR, "批量导入信息异常（第6行）：家长电话格式不正确（应为11位手机号）；缺性别");
        timeline.event(chen, com.county.vision.domain.enums.EventType.IMPORT, "名单导入（批次 错误演示批次，第6行）");

        // ---- 6. 复诊结果与筛查差异较大（刘洋，七年级2班，实验中学）----
        GradeClass g72 = grade(s3, "七年级", "2班");
        Student liu = student(s3, g72, "2023070206", "刘洋", "男",
                LocalDate.of(2012, 4, 8), "刘爸爸", "13800006666", "小学毕业视力5.0", year);
        flow.submitScreen(screen(liu.getId(), "4.8", "4.8", "-0.75", "-0.75", null, null,
                false, "用眼习惯一般", false, null, d(2026, 9, 18)));
        flow.schoolReview(review(liu.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(liu.getId(), "短信", false, null));
        flow.parentChoice(choice(liu.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(liu.getId(), "阳光县人民医院眼科", "刘医生", d(2026, 9, 28),
                false, false, "0.00", "0.00", null, null, null, null, "62",
                "散瞳后双眼正视，筛查时疑似调节痉挛导致的假性近视", 6, false, true,
                "筛查球镜-0.75D，散瞳后0.00D，差异较大，建议复查筛查设备"));

        // ---- 7. 家长自行外配眼镜 + 补助资格争议（孙磊，七年级1班）----
        GradeClass g71 = grade(s3, "七年级", "1班");
        Student sun = student(s3, g71, "2023070107", "孙磊", "男",
                LocalDate.of(2012, 9, 30), "孙妈妈", "13800007777", null, year);
        flow.submitScreen(screen(sun.getId(), "4.4", "4.5", "-2.25", "-2.00", "-0.50", "-0.50",
                false, "作业时间长", false, null, d(2026, 9, 18)));
        flow.schoolReview(review(sun.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(sun.getId(), "APP", false, null));
        flow.parentChoice(choice(sun.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(sun.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 27),
                true, true, "-2.25", "-2.00", "-0.50", "-0.50", "180", "180", "60",
                "双眼中度近视", 6, true, false, null));
        flow.submitGlasses(glasses(sun.getId(), "外地某眼镜商城（自行外配）", d(2026, 10, 3),
                false, new BigDecimal("420.00"), "无医学验光单，参数不详", false, null));
        Long app7 = flow.applySubsidy(subsidy(sun.getId(), new BigDecimal("300.00"), "家长自行外配，申请补助")).getId();
        flow.approveSubsidy(approve(app7, "DISPUTE", null, "非合作机构外配且无医学验光凭证，补助资格进入争议复核"));

        // ---- 8. 配镜后不适（周婷，高一3班，西乡中学）----
        GradeClass g103 = grade(s4, "高一", "3班");
        Student zhou = student(s4, g103, "2021100308", "周婷", "女",
                LocalDate.of(2009, 12, 3), "周爸爸", "13800008888", "初中起近视", year);
        flow.submitScreen(screen(zhou.getId(), "4.3", "4.6", "-3.00", "-1.50", "-1.00", null,
                true, "已戴旧镜，每日学习10小时", false, null, d(2026, 9, 20)));
        flow.schoolReview(review(zhou.getId(), "CONFIRM", "已戴镜学生视力仍不达标"));
        flow.notifyParent(notify(zhou.getId(), "家长会告知", false, null));
        flow.parentChoice(choice(zhou.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(zhou.getId(), "阳光县人民医院眼科", "吴医生", d(2026, 9, 29),
                true, true, "-3.25", "-1.75", "-1.00", "-0.25", "180", "5", "59",
                "近视进展，重新配镜", 6, true, false, null));
        flow.submitGlasses(glasses(zhou.getId(), "县人民医院配镜中心", d(2026, 10, 4),
                true, new BigDecimal("860.00"), "1.60镜片", true, "佩戴后头晕、看地面变形，已联系门诊"));

        // ---- 9. 半年复查未到场（吴敏，东乡小学五年级2班）----
        GradeClass g52 = grade(s2, "五年级", "2班");
        Student wu = student(s2, g52, "2022050209", "吴敏", "女",
                LocalDate.of(2013, 2, 14), "吴妈妈", "13800009999", "2025年已配镜", year);
        flow.submitScreen(screen(wu.getId(), "4.6", "4.6", "-1.75", "-1.75", null, null,
                true, "戴镜视力不稳定", false, null, d(2026, 3, 10)));
        flow.schoolReview(review(wu.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(wu.getId(), "短信", false, null));
        flow.parentChoice(choice(wu.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(wu.getId(), "爱眼眼科门诊", "陈医生", d(2026, 3, 18),
                true, false, "-1.75", "-1.75", null, null, null, null, "57",
                "低度近视", 6, true, false, null));
        flow.submitGlasses(glasses(wu.getId(), "爱眼门诊配镜部", d(2026, 3, 25),
                true, new BigDecimal("600.00"), "1.56镜片", false, null));
        flow.followUp(followup(wu.getId(), null, null, "NO_SHOW", "爱眼眼科门诊", null, "9月半年复查未到场"));

        // ---- 10. 家长选择暂缓（郑爽，三年级2班）----
        Student zheng = student(s1, grade(s1, "三年级", "2班"), "2024030210", "郑爽", "女",
                LocalDate.of(2014, 7, 7), "郑妈妈", "13800010000", null, year);
        flow.submitScreen(screen(zheng.getId(), "4.8", "4.9", "-0.50", "0.00", null, null,
                false, "近距离用眼偏多", false, null, d(2026, 9, 15)));
        flow.schoolReview(review(zheng.getId(), "CONFIRM", "临界异常，建议复诊确认"));
        flow.notifyParent(notify(zheng.getId(), "电话", false, null));
        flow.parentChoice(choice(zheng.getId(), "DEFER", null, "孩子年龄小，家长想先观察两个月再决定是否复诊", null));

        // ---- 11. 筛查正常（冯军，四年级1班）----
        Student feng = student(s1, grade(s1, "四年级", "1班"), "2024040111", "冯军", "男",
                LocalDate.of(2013, 10, 1), "冯爸爸", "13800010111", "既往无异常", year);
        flow.submitScreen(screen(feng.getId(), "5.0", "5.0", "0.00", "0.00", null, null,
                false, "用眼习惯良好，每日户外2小时", false, null, d(2026, 9, 16)));

        // ---- 12. 建议复测（褚琳，七年级2班）----
        Student chu = student(s3, g72, "2023070212", "褚琳", "女",
                LocalDate.of(2012, 5, 19), "褚妈妈", "13800010222", null, year);
        flow.submitScreen(screen(chu.getId(), "4.9", "4.6", "0.00", "-0.50", null, null,
                false, "检查时状态不佳", true, null, d(2026, 9, 18)));
        flow.schoolReview(review(chu.getId(), "RETEST", "双眼结果差异大，建议复测后再告知家长"));

        // ---- 13. 尚未筛查（卫兰，三年级3班）----
        student(s1, gc33, "2024030313", "卫兰", "女", LocalDate.of(2014, 3, 30),
                "卫爸爸", "13800010333", null, year);

        // ---- 14. 请假后补检完成、全流程闭环（何军，东乡五年级2班）----
        Student he = student(s2, g52, "2022050214", "何军", "男",
                LocalDate.of(2013, 4, 23), "何妈妈", "13800010444", null, year);
        RecordDtos.ScreenRequest heAbsent = new RecordDtos.ScreenRequest();
        heAbsent.setStudentId(he.getId());
        heAbsent.setAbsent(true);
        heAbsent.setScreenDate(d(2026, 9, 12));
        heAbsent.setOrgName("阳光县视光中心");
        heAbsent.setDeviceCalibNo("DEV-2026-0091");
        flow.submitScreen(heAbsent);
        // 一周后补检
        flow.submitScreen(screen(he.getId(), "4.7", "4.8", "-1.00", "-0.75", null, null,
                false, "补检配合良好", false, null, d(2026, 9, 19)));
        resolveOpen(he.getId(), ScreenFlag.ABSENT, "学生已于9月19日完成补检");
        flow.schoolReview(review(he.getId(), "CONFIRM", "补检结果有效，确认异常"));
        flow.notifyParent(notify(he.getId(), "电话", false, null));
        flow.parentChoice(choice(he.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(he.getId(), "阳光县人民医院眼科", "刘医生", d(2026, 9, 26),
                true, false, "-1.00", "-0.75", null, null, null, null, "58",
                "低度近视", 6, true, false, null));
        flow.submitGlasses(glasses(he.getId(), "县人民医院配镜中心", d(2026, 10, 2),
                true, new BigDecimal("520.00"), "1.56镜片", false, null));
        flow.addIntervention(intervention(he.getId(), "SEAT_ADJUST", d(2026, 9, 30), "座位调整至前排"));

        // ---- 15. 配镜后不适、已复诊调整闭环（沈月，西乡高一3班）----
        Student shen = student(s4, g103, "2021100315", "沈月", "女",
                LocalDate.of(2009, 8, 8), "沈妈妈", "13800010555", "初中已近视", year);
        flow.submitScreen(screen(shen.getId(), "4.5", "4.5", "-1.75", "-1.75", null, null,
                true, "学习时间较长", false, null, d(2026, 9, 20)));
        flow.schoolReview(review(shen.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(shen.getId(), "短信", false, null));
        flow.parentChoice(choice(shen.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(shen.getId(), "阳光县人民医院眼科", "吴医生", d(2026, 9, 28),
                true, true, "-2.00", "-2.00", null, null, null, null, "59",
                "低度近视", 6, true, false, null));
        var shenGlasses = flow.submitGlasses(glasses(shen.getId(), "县人民医院配镜中心", d(2026, 10, 3),
                true, new BigDecimal("720.00"), "1.56镜片", true, "初戴头晕、视物变形"));
        flow.resolveDiscomfort(shenGlasses.getId(), "复查发现瞳距偏差，重新加工镜片后佩戴正常");

        // ---- 16. 电话两次不通后接通、确认并闭环（韩雪，三年级3班）----
        Student han = student(s1, gc33, "2024030316", "韩雪", "女",
                LocalDate.of(2014, 12, 17), "韩爸爸", "13800010666", null, year);
        flow.submitScreen(screen(han.getId(), "4.6", "4.7", "-1.25", "-1.00", null, null,
                false, "户外活动不足", false, null, d(2026, 9, 15)));
        flow.schoolReview(review(han.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(han.getId(), "电话", true, null));
        flow.notifyParent(notify(han.getId(), "电话", true, null));
        flow.notifyParent(notify(han.getId(), "电话", false, "第三次拨打接通"));
        flow.parentChoice(choice(han.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, "家长已接通并确认"));
        flow.submitVisit(visit(han.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 30),
                true, false, "-1.25", "-1.00", null, null, null, null, "57",
                "低度近视", 6, true, false, null));
        flow.submitGlasses(glasses(han.getId(), "爱眼门诊配镜部", d(2026, 10, 5),
                true, new BigDecimal("540.00"), "1.56镜片", false, null));
        flow.addIntervention(intervention(han.getId(), "PARENT_REMIND", d(2026, 10, 6), "提醒家长督促每日2小时户外活动"));

        /* ================= 合作门店配镜补助发放核验场景 ================= */

        // ---- 17. 门店核验通过、补助已发放（林涛，五年级1班）----
        Student lin = student(s1, grade(s1, "五年级", "1班"), "2024050117", "林涛", "男",
                LocalDate.of(2013, 5, 6), "林妈妈", "13800010777", null, year);
        flow.submitScreen(screen(lin.getId(), "4.6", "4.7", "-1.50", "-1.25", "-0.50", "-0.50",
                false, "作业时间长", false, null, d(2026, 9, 22)));
        flow.schoolReview(review(lin.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(lin.getId(), "短信", false, null));
        flow.parentChoice(choice(lin.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(lin.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 28),
                true, false, "-1.50", "-1.25", "-0.50", "-0.50", "180", "175", "59",
                "双眼真性近视", 6, true, false, null));
        GlassesRecord linG = flow.submitGlasses(glasses(lin.getId(), "阳光眼镜合作门店", d(2026, 10, 2),
                true, new BigDecimal("680.00"), "1.60防蓝光非球面镜片", false, null));
        Long linD = verify.submit(dispense(lin.getId(), linG.getId(),
                "-1.50", "-1.25", "-0.50", "-0.50", "180", "175", "59",
                "1.60防蓝光非球面", "480.00", "200.00", "PAY-2026-10001", d(2026, 10, 5), "林妈妈")).getId();
        verify.pay(linD);

        // ---- 18. 付款凭证重复 → 核验挂起（周杰，八年级1班）----
        Student zhouJ = student(s3, grade(s3, "八年级", "1班"), "2023080118", "周杰", "男",
                LocalDate.of(2011, 9, 15), "周爸爸", "13800010888", null, year);
        flow.submitScreen(screen(zhouJ.getId(), "4.5", "4.6", "-2.00", "-1.75", "-0.25", "-0.25",
                false, "电子产品使用偏多", false, null, d(2026, 9, 24)));
        flow.schoolReview(review(zhouJ.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(zhouJ.getId(), "电话", false, null));
        flow.parentChoice(choice(zhouJ.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(zhouJ.getId(), "阳光县人民医院眼科", "刘医生", d(2026, 9, 30),
                true, true, "-2.00", "-1.75", "-0.25", "-0.25", "180", "180", "61",
                "双眼中度近视", 6, true, false, null));
        GlassesRecord zhouG = flow.submitGlasses(glasses(zhouJ.getId(), "阳光眼镜合作门店", d(2026, 10, 4),
                true, new BigDecimal("720.00"), "1.60镜片", false, null));
        // 门店误用林涛的付款凭证号上传 → 平台核验挂起
        verify.submit(dispense(zhouJ.getId(), zhouG.getId(),
                "-2.00", "-1.75", "-0.25", "-0.25", "180", "180", "61",
                "1.60非球面", "520.00", "200.00", "PAY-2026-10001", d(2026, 10, 6), "周爸爸"));

        // ---- 19. 门店重复提交同一副眼镜 → 挂起并拦截重复发放（郑凯，四年级2班）----
        Student zhengK = student(s1, grade(s1, "四年级", "2班"), "2024040219", "郑凯", "男",
                LocalDate.of(2013, 8, 21), "郑妈妈", "13800010999", null, year);
        flow.submitScreen(screen(zhengK.getId(), "4.7", "4.8", "-1.25", "-1.00", null, null,
                false, "户外活动不足", false, null, d(2026, 9, 23)));
        flow.schoolReview(review(zhengK.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(zhengK.getId(), "APP", false, null));
        flow.parentChoice(choice(zhengK.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(zhengK.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 29),
                true, false, "-1.25", "-1.00", null, null, null, null, "58",
                "双眼低度近视", 6, true, false, null));
        GlassesRecord zhengG = flow.submitGlasses(glasses(zhengK.getId(), "阳光眼镜合作门店", d(2026, 10, 3),
                true, new BigDecimal("560.00"), "1.56树脂片", false, null));
        Long zhengD = verify.submit(dispense(zhengK.getId(), zhengG.getId(),
                "-1.25", "-1.00", null, null, null, null, "58",
                "1.56树脂片", "400.00", "160.00", "PAY-2026-10003", d(2026, 10, 5), "郑妈妈")).getId();
        verify.pay(zhengD);
        // 门店就同一副眼镜再次提交 → 挂起，补助不会发第二次
        verify.submit(dispense(zhengK.getId(), zhengG.getId(),
                "-1.25", "-1.00", null, null, null, null, "58",
                "1.56树脂片", "400.00", "160.00", "PAY-2026-10003B", d(2026, 10, 6), "郑妈妈"));

        // ---- 20. 退镜 → 差额追回已结算（孙悦，高一1班）----
        Student sunY = student(s4, grade(s4, "高一", "1班"), "2021100120", "孙悦", "女",
                LocalDate.of(2009, 4, 18), "孙妈妈", "13800011000", "初中起近视", year);
        flow.submitScreen(screen(sunY.getId(), "4.4", "4.5", "-2.50", "-2.25", "-0.50", "-0.75",
                true, "已戴旧镜", false, null, d(2026, 9, 21)));
        flow.schoolReview(review(sunY.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(sunY.getId(), "短信", false, null));
        flow.parentChoice(choice(sunY.getId(), "COUNTY_HOSPITAL", "阳光县人民医院眼科", null, null));
        flow.submitVisit(visit(sunY.getId(), "阳光县人民医院眼科", "吴医生", d(2026, 9, 27),
                true, true, "-2.50", "-2.25", "-0.50", "-0.75", "180", "180", "60",
                "双眼中度近视，重新配镜", 6, true, false, null));
        GlassesRecord sunG = flow.submitGlasses(glasses(sunY.getId(), "阳光眼镜合作门店", d(2026, 10, 1),
                true, new BigDecimal("680.00"), "1.60镜片", false, null));
        Long sunD = verify.submit(dispense(sunY.getId(), sunG.getId(),
                "-2.50", "-2.25", "-0.50", "-0.75", "180", "180", "60",
                "1.60非球面", "480.00", "200.00", "PAY-2026-10004", d(2026, 10, 3), "孙妈妈")).getId();
        verify.pay(sunD);
        Long sunAs = verify.registerAfterSales(afterSales(sunD, "RETURN",
                "家长对镜片效果不满意，与门店协商退镜", "680.00", null)).getId();
        verify.settle(sunAs);

        // ---- 21. 换片 → 应补发差额待结算（钱进，六年级1班）----
        Student qian = student(s2, grade(s2, "六年级", "1班"), "2022060121", "钱进", "男",
                LocalDate.of(2012, 7, 9), "钱爸爸", "13800011111", null, year);
        flow.submitScreen(screen(qian.getId(), "4.6", "4.7", "-1.75", "-1.50", "-0.25", "-0.25",
                false, "用眼习惯一般", false, null, d(2026, 9, 22)));
        flow.schoolReview(review(qian.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(qian.getId(), "电话", false, null));
        flow.parentChoice(choice(qian.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(qian.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 28),
                true, false, "-1.75", "-1.50", "-0.25", "-0.25", "175", "180", "58",
                "双眼低度近视", 6, true, false, null));
        GlassesRecord qianG = flow.submitGlasses(glasses(qian.getId(), "阳光眼镜合作门店", d(2026, 10, 2),
                true, new BigDecimal("560.00"), "1.56树脂片", false, null));
        Long qianD = verify.submit(dispense(qian.getId(), qianG.getId(),
                "-1.75", "-1.50", "-0.25", "-0.25", "175", "180", "58",
                "1.56树脂片", "400.00", "160.00", "PAY-2026-10005", d(2026, 10, 4), "钱爸爸")).getId();
        verify.pay(qianD);
        // 镜片刮花更换防蓝光镜片，新费用 760 元 → 应补发 20 元，待卫健结算
        verify.registerAfterSales(afterSales(qianD, "LENS_CHANGE",
                "镜片刮花，更换1.60防蓝光镜片", "0.00", "760.00"));

        // ---- 22. 不适反馈已调整，适应回访提醒中（李欣，三年级1班）----
        Student liX = student(s1, grade(s1, "三年级", "1班"), "2024030122", "李欣", "女",
                LocalDate.of(2014, 10, 12), "李妈妈", "13800011222", null, year);
        flow.submitScreen(screen(liX.getId(), "4.7", "4.8", "-1.00", "-0.75", null, null,
                false, "读写姿势不规范", false, null, d(2026, 9, 23)));
        flow.schoolReview(review(liX.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(liX.getId(), "短信", false, null));
        flow.parentChoice(choice(liX.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(liX.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 29),
                true, false, "-1.00", "-0.75", null, null, null, null, "57",
                "双眼低度近视", 6, true, false, null));
        GlassesRecord liXG = flow.submitGlasses(glasses(liX.getId(), "阳光眼镜合作门店", d(2026, 10, 3),
                true, new BigDecimal("540.00"), "1.56树脂片", false, null));
        Long liXD = verify.submit(dispense(liX.getId(), liXG.getId(),
                "-1.00", "-0.75", null, null, null, null, "57",
                "1.56树脂片", "380.00", "160.00", "PAY-2026-10006", d(2026, 10, 5), "李妈妈")).getId();
        verify.pay(liXD);
        Long liXAs = verify.feedback(feedback(liXD, "戴镜头晕、看近疲劳")).getId();
        verify.uploadAdjustResult(liXAs, "复查光度无误，调整鼻托与镜架倾角后佩戴适应良好");

        // ---- 23. 不适反馈待门店调整（吴忧，五年级1班）----
        Student wuY = student(s2, grade(s2, "五年级", "1班"), "2022050123", "吴忧", "女",
                LocalDate.of(2013, 3, 27), "吴妈妈", "13800011333", null, year);
        flow.submitScreen(screen(wuY.getId(), "4.6", "4.6", "-1.50", "-1.25", "-0.25", "-0.25",
                false, "每日近距离用眼约4小时", false, null, d(2026, 9, 24)));
        flow.schoolReview(review(wuY.getId(), "CONFIRM", "确认异常"));
        flow.notifyParent(notify(wuY.getId(), "APP", false, null));
        flow.parentChoice(choice(wuY.getId(), "PARTNER_CLINIC", "爱眼眼科门诊", null, null));
        flow.submitVisit(visit(wuY.getId(), "爱眼眼科门诊", "陈医生", d(2026, 9, 30),
                true, false, "-1.50", "-1.25", "-0.25", "-0.25", "180", "180", "58",
                "双眼低度近视", 6, true, false, null));
        GlassesRecord wuG = flow.submitGlasses(glasses(wuY.getId(), "阳光眼镜合作门店", d(2026, 10, 4),
                true, new BigDecimal("600.00"), "1.56树脂片", false, null));
        Long wuD = verify.submit(dispense(wuY.getId(), wuG.getId(),
                "-1.50", "-1.25", "-0.25", "-0.25", "180", "180", "58",
                "1.56树脂片", "420.00", "180.00", "PAY-2026-10007", d(2026, 10, 6), "吴妈妈")).getId();
        verify.pay(wuD);
        verify.feedback(feedback(wuD, "镜架夹头、看不清黑板"));

        log.info("种子数据初始化完成：3 个片区、4 所学校、11 个角色账号、23 名学生场景样例"
                + "（含门店核验通过/挂起、退镜追回、换片补差、不适调整与适应回访场景）");
    }

    /** 闭环某学生当前未处理的指定类型异常事项 */
    private void resolveOpen(Long studentId, ScreenFlag flag, String note) {
        issueRepo.findFirstByStudentIdAndIssueTypeAndStatus(studentId, flag.name(), "OPEN")
                .ifPresent(it -> flow.resolveIssue(
                        new RecordDtos.IssueResolveRequest() {{
                            setIssueId(it.getId());
                            setResolveNote(note);
                        }}));
    }

    /* ---------- 构造请求的小工具 ---------- */

    private LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    private RecordDtos.ScreenRequest screen(Long sid, String vl, String vr, String sl, String sr,
                                            String cl, String cr, boolean wearing, String habit,
                                            boolean retest, Void unused, LocalDate date) {
        RecordDtos.ScreenRequest r = new RecordDtos.ScreenRequest();
        r.setStudentId(sid);
        r.setScreenDate(date);
        r.setOrgName("阳光县视光中心");
        r.setOperatorName("筛查队-小马");
        r.setDeviceCalibNo("DEV-2026-0091");
        r.setNakedVisionLeft(vl);
        r.setNakedVisionRight(vr);
        r.setSphereLeft(sl);
        r.setSphereRight(sr);
        r.setCylinderLeft(cl);
        r.setCylinderRight(cr);
        r.setWearingGlasses(wearing);
        r.setHabitSummary(habit);
        r.setRetestNeeded(retest);
        r.setAbsent(false);
        return r;
    }

    private RecordDtos.SchoolReviewRequest review(Long sid, String result, String opinion) {
        RecordDtos.SchoolReviewRequest r = new RecordDtos.SchoolReviewRequest();
        r.setStudentId(sid);
        r.setResult(result);
        r.setOpinion(opinion);
        return r;
    }

    private RecordDtos.NotifyRequest notify(Long sid, String channel, boolean unreachable, String remark) {
        RecordDtos.NotifyRequest r = new RecordDtos.NotifyRequest();
        r.setStudentId(sid);
        r.setChannel(channel);
        r.setUnreachable(unreachable);
        r.setRemark(remark);
        return r;
    }

    private RecordDtos.ParentChoiceRequest choice(Long sid, String choice, String org, String deferReason, String remark) {
        RecordDtos.ParentChoiceRequest r = new RecordDtos.ParentChoiceRequest();
        r.setStudentId(sid);
        r.setChoice(choice);
        r.setTargetOrg(org);
        r.setDeferReason(deferReason);
        r.setRemark(remark);
        return r;
    }

    private RecordDtos.VisitRequest visit(Long sid, String org, String doctor, LocalDate date,
                                          Boolean trueMyopia, Boolean myd, String sl, String sr,
                                          String cl, String cr, String al, String ar, String pd,
                                          String diagnosis, Integer cycle, Boolean subsidy,
                                          Boolean bigDiff, String diffNote) {
        RecordDtos.VisitRequest v = new RecordDtos.VisitRequest();
        v.setStudentId(sid);
        v.setOrgName(org);
        v.setDoctor(doctor);
        v.setVisitDate(date);
        v.setTrueMyopia(trueMyopia);
        v.setNeedMydriasis(myd);
        v.setRxSphereLeft(sl);
        v.setRxSphereRight(sr);
        v.setRxCylinderLeft(cl);
        v.setRxCylinderRight(cr);
        v.setRxAxisLeft(al);
        v.setRxAxisRight(ar);
        v.setPd(pd);
        v.setDiagnosis(diagnosis);
        v.setReviewCycleMonths(cycle);
        v.setSubsidyEligible(subsidy);
        v.setBigDifference(bigDiff);
        v.setDifferenceNote(diffNote);
        return v;
    }

    private RecordDtos.GlassesRequest glasses(Long sid, String org, LocalDate date, boolean partner,
                                              BigDecimal amount, String lens, boolean discomfort, String detail) {
        RecordDtos.GlassesRequest g = new RecordDtos.GlassesRequest();
        g.setStudentId(sid);
        g.setOpticianOrg(org);
        g.setGlassesDate(date);
        g.setPartnerOptician(partner);
        g.setAmount(amount);
        g.setLensInfo(lens);
        g.setDiscomfort(discomfort);
        g.setDiscomfortDetail(detail);
        return g;
    }

    private RecordDtos.SubsidyApplyRequest subsidy(Long sid, BigDecimal amount, String opinion) {
        RecordDtos.SubsidyApplyRequest r = new RecordDtos.SubsidyApplyRequest();
        r.setStudentId(sid);
        r.setApplyAmount(amount);
        r.setOpinion(opinion);
        return r;
    }

    private RecordDtos.SubsidyApproveRequest approve(Long appId, String action, BigDecimal amount, String opinion) {
        RecordDtos.SubsidyApproveRequest r = new RecordDtos.SubsidyApproveRequest();
        r.setApplicationId(appId);
        r.setAction(action);
        r.setApprovedAmount(amount);
        r.setOpinion(opinion);
        return r;
    }

    private RecordDtos.FollowUpRequest followup(Long sid, LocalDate planned, LocalDate actual,
                                                String action, String org, String result, String remark) {
        RecordDtos.FollowUpRequest f = new RecordDtos.FollowUpRequest();
        f.setStudentId(sid);
        f.setPlannedDate(planned);
        f.setActualDate(actual);
        f.setAction(action);
        f.setOrgName(org);
        f.setVisionResult(result);
        f.setRemark(remark);
        return f;
    }

    private RecordDtos.InterventionRequest intervention(Long sid, String type, LocalDate date, String content) {
        RecordDtos.InterventionRequest i = new RecordDtos.InterventionRequest();
        i.setStudentId(sid);
        i.setType(type);
        i.setExecDate(date);
        i.setContent(content);
        i.setExecuted(true);
        return i;
    }

    /** 门店核验材料：处方与复诊一致、材料齐全、凭证唯一 → 核验通过 */
    private VerifyDtos.DispenseSubmitRequest dispense(Long sid, Long gid, String sl, String sr,
                                                      String cl, String cr, String al, String ar, String pd,
                                                      String lensParams, String lensAmt, String frameAmt,
                                                      String voucher, LocalDate pickupDate, String signer) {
        VerifyDtos.DispenseSubmitRequest r = new VerifyDtos.DispenseSubmitRequest();
        r.setStudentId(sid);
        r.setGlassesId(gid);
        r.setStoreName("阳光眼镜合作门店");
        r.setRxSphereLeft(sl);
        r.setRxSphereRight(sr);
        r.setRxCylinderLeft(cl);
        r.setRxCylinderRight(cr);
        r.setRxAxisLeft(al);
        r.setRxAxisRight(ar);
        r.setPd(pd);
        r.setLensParams(lensParams);
        r.setLensAmount(new BigDecimal(lensAmt));
        r.setFrameAmount(new BigDecimal(frameAmt));
        r.setTotalAmount(new BigDecimal(lensAmt).add(new BigDecimal(frameAmt)));
        r.setPaymentVoucherNo(voucher);
        r.setPickupSigned(true);
        r.setPickupDate(pickupDate);
        r.setPickupSigner(signer);
        return r;
    }

    private VerifyDtos.AfterSalesRequest afterSales(Long dispenseId, String type,
                                                    String reason, String refund, String newAmount) {
        VerifyDtos.AfterSalesRequest r = new VerifyDtos.AfterSalesRequest();
        r.setDispenseId(dispenseId);
        r.setType(type);
        r.setReason(reason);
        r.setRefundAmount(refund == null ? null : new BigDecimal(refund));
        r.setNewAmount(newAmount == null ? null : new BigDecimal(newAmount));
        return r;
    }

    private VerifyDtos.FeedbackRequest feedback(Long dispenseId, String text) {
        VerifyDtos.FeedbackRequest r = new VerifyDtos.FeedbackRequest();
        r.setDispenseId(dispenseId);
        r.setFeedback(text);
        return r;
    }

    /* ---------- 实体构造 ---------- */

    private Region region(String name, String code) {
        Region r = new Region();
        r.setName(name);
        r.setCode(code);
        return regionRepo.save(r);
    }

    private School school(String name, Region region, String address, String healthTeacher) {
        School s = new School();
        s.setName(name);
        s.setRegion(region);
        s.setAddress(address);
        s.setHealthTeacher(healthTeacher);
        s.setContactPhone("0551-8000" + (1000 + schoolRepo.count()));
        return schoolRepo.save(s);
    }

    private GradeClass grade(School s, String grade, String className) {
        return classRepo.findBySchoolAndGradeAndClassName(s, grade, className).orElseGet(() -> {
            GradeClass gc = new GradeClass();
            gc.setSchool(s);
            gc.setGrade(grade);
            gc.setClassName(className);
            return classRepo.save(gc);
        });
    }

    private Student student(School school, GradeClass gc, String no, String name, String gender,
                            LocalDate birth, String parentName, String phone, String archive, int year) {
        Student s = new Student();
        s.setSchool(school);
        s.setGradeClass(gc);
        s.setStudentNo(no);
        s.setName(name);
        s.setGender(gender);
        s.setBirthDate(birth);
        s.setParentName(parentName);
        s.setParentPhone(phone);
        s.setPriorVisionArchive(archive);
        s.setScreenYear(year);
        s.setImportBatch("seed-batch");
        return studentRepo.save(s);
    }

    private void account(String username, String pwd, String realName, Role role,
                         School school, String orgName, String phone) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setRealName(realName);
        u.setRole(role);
        u.setSchool(school);
        u.setOrgName(orgName);
        u.setPhone(phone);
        userRepo.save(u);
    }
}
