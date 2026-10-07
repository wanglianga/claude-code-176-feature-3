package com.county.vision.service;

import com.county.vision.cache.CacheService;
import com.county.vision.domain.entity.School;
import com.county.vision.domain.entity.Student;
import com.county.vision.domain.enums.ScreenFlag;
import com.county.vision.domain.enums.StudentStage;
import com.county.vision.domain.repository.*;
import com.county.vision.security.LoginUser;
import com.county.vision.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 县教体/卫健部门监管看板：趋势、真实复诊率、干预执行、补助发放、异常闭环 */
@Service
public class StatsService {

    private final StudentRepository studentRepo;
    private final ScreenRecordRepository screenRepo;
    private final VisitRecordRepository visitRepo;
    private final GlassesRecordRepository glassesRepo;
    private final SubsidyApplicationRepository subsidyRepo;
    private final IssueTrackerRepository issueRepo;
    private final SchoolRepository schoolRepo;
    private final StoreDispenseRepository dispenseRepo;
    private final AfterSalesRepository afterSalesRepo;
    private final CacheService cache;

    @PersistenceContext
    private EntityManager em;

    public StatsService(StudentRepository studentRepo, ScreenRecordRepository screenRepo,
                        VisitRecordRepository visitRepo, GlassesRecordRepository glassesRepo,
                        SubsidyApplicationRepository subsidyRepo, IssueTrackerRepository issueRepo,
                        SchoolRepository schoolRepo, StoreDispenseRepository dispenseRepo,
                        AfterSalesRepository afterSalesRepo, CacheService cache) {
        this.studentRepo = studentRepo;
        this.screenRepo = screenRepo;
        this.visitRepo = visitRepo;
        this.glassesRepo = glassesRepo;
        this.subsidyRepo = subsidyRepo;
        this.issueRepo = issueRepo;
        this.schoolRepo = schoolRepo;
        this.dispenseRepo = dispenseRepo;
        this.afterSalesRepo = afterSalesRepo;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long schoolId, Long regionId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();

        String cacheKey = "stats:overview:" + schoolId + ":" + regionId;
        Map<String, Object> cached = cache.get(cacheKey, Map.class);
        if (cached != null) return cached;

        List<Student> students = studentRepo.search(schoolId, null, regionId, null, null, false);
        long total = students.size();
        long screened = students.stream().filter(s -> s.getStage() != StudentStage.PENDING_SCREEN
                && s.getStage() != StudentStage.LOST_FOLLOW).count();
        long abnormalStudents = students.stream().filter(this::isAbnormal).count();
        long visitCompleted = students.stream().filter(this::hasVisited).count();
        long trueMyopia = visitRepo.countByTrueMyopiaTrue();
        long managing = students.stream().filter(s -> s.getStage() == StudentStage.MANAGING
                || s.getStage() == StudentStage.CLOSED_LOOP).count();
        long deferred = students.stream().filter(s -> s.getStage() == StudentStage.DEFERRED).count();
        long lostFollow = students.stream().filter(s -> s.getStage() == StudentStage.LOST_FOLLOW).count();
        long reviewDue = students.stream().filter(s -> s.getStage() == StudentStage.REVIEW_DUE).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalStudents", total);
        m.put("screenedCount", screened);
        m.put("abnormalCount", abnormalStudents);
        m.put("trueMyopiaCount", trueMyopia);
        m.put("visitedCount", visitCompleted);
        m.put("managingCount", managing);
        m.put("deferredCount", deferred);
        m.put("lostFollowCount", lostFollow);
        m.put("reviewDueCount", reviewDue);
        m.put("externalGlasses", glassesRepo.countByPartnerOpticianFalse());
        m.put("discomfortOpen", glassesRepo.countByDiscomfortTrueAndDiscomfortResolvedFalse());
        m.put("openIssues", issueRepo.countByStatus("OPEN"));
        m.put("resolvedIssues", issueRepo.countByStatus("RESOLVED"));
        m.put("screenRate", rate(screened, total));
        m.put("abnormalRate", rate(abnormalStudents, total));
        // 真实复诊率 = 已完成复诊人数 / 筛查异常人数（卫健核对：避免异常学生无人跟进）
        m.put("realVisitRate", rate(visitCompleted, abnormalStudents));
        m.put("closedLoopRate", rate(issueRepo.countByStatus("RESOLVED"),
                issueRepo.countByStatus("OPEN") + issueRepo.countByStatus("RESOLVED")));
        m.put("subsidyPaid", subsidyRepo.countByStatus(
                com.county.vision.domain.enums.SubsidyStatus.PAID));
        m.put("subsidyDispute", subsidyRepo.countByStatus(
                com.county.vision.domain.enums.SubsidyStatus.DISPUTE));
        // 门店配镜核验：挂起待更正 / 核验通过待发放 / 售后待结算或待调整
        m.put("dispenseSuspended", dispenseRepo.countByStatus(
                com.county.vision.domain.enums.DispenseStatus.SUSPENDED));
        m.put("dispensePassed", dispenseRepo.countByStatus(
                com.county.vision.domain.enums.DispenseStatus.PASSED));
        m.put("afterSalesOpen", afterSalesRepo.countByStatus("PENDING_SETTLE")
                + afterSalesRepo.countByStatus("WAIT_ADJUST"));

        cache.put(cacheKey, m, Duration.ofSeconds(30));
        return m;
    }

    private boolean isAbnormal(Student s) {
        // 经筛查/复核确认进入异常管理链路（请假失访与筛查正常不计入异常分母）
        return s.getStage() != StudentStage.PENDING_SCREEN
                && s.getStage() != StudentStage.SCREENED
                && s.getStage() != StudentStage.LOST_FOLLOW;
    }

    private boolean hasVisited(Student s) {
        return switch (s.getStage()) {
            case VISITED, GLASSES_SUBSIDY, MANAGING, REVIEW_DUE, CLOSED_LOOP -> true;
            default -> false;
        };
    }

    private double rate(long part, long total) {
        if (total == 0) return 0.0;
        return Math.round(part * 10000.0 / total) / 100.0;
    }

    /** 按年级观察近视发展趋势 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> trendByGrade(Long schoolId, Long regionId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();
        List<Student> students = studentRepo.search(schoolId, null, regionId, null, null, false);

        Map<String, long[]> byGrade = new LinkedHashMap<>();
        for (Student s : students) {
            String grade = s.getGradeClass().getGrade();
            long[] arr = byGrade.computeIfAbsent(grade, k -> new long[4]); // total, abnormal, visited, trueMyopia-ish
            arr[0]++;
            if (isAbnormal(s)) arr[1]++;
            if (hasVisited(s)) arr[2]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byGrade.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grade", e.getKey());
            row.put("total", e.getValue()[0]);
            row.put("abnormal", e.getValue()[1]);
            row.put("visited", e.getValue()[2]);
            row.put("abnormalRate", rate(e.getValue()[1], e.getValue()[0]));
            row.put("visitRate", rate(e.getValue()[2], e.getValue()[1]));
            out.add(row);
        });
        return out;
    }

    /** 按学校对比（片区/全县排名） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> bySchool(Long regionId) {
        List<School> schools = regionId == null ? schoolRepo.findAllByOrderByIdAsc()
                : schoolRepo.findByRegionId(regionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (School sch : schools) {
            List<Student> students = studentRepo.findBySchoolIdOrderByIdAsc(sch.getId());
            long total = students.size();
            long abnormal = students.stream().filter(this::isAbnormal).count();
            long visited = students.stream().filter(this::hasVisited).count();
            long interventions = ((Number) em.createQuery(
                            "select count(i) from Intervention i where i.student.school.id = :sid")
                    .setParameter("sid", sch.getId()).getSingleResult()).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schoolId", sch.getId());
            row.put("schoolName", sch.getName());
            row.put("regionName", sch.getRegion() != null ? sch.getRegion().getName() : "—");
            row.put("total", total);
            row.put("abnormal", abnormal);
            row.put("visited", visited);
            row.put("abnormalRate", rate(abnormal, total));
            row.put("realVisitRate", rate(visited, abnormal));
            row.put("interventions", interventions);
            out.add(row);
        }
        return out;
    }

    /** 按片区观察 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> byRegion() {
        List<School> schools = schoolRepo.findAllByOrderByIdAsc();
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (School sch : schools) {
            String region = sch.getRegion() != null ? sch.getRegion().getName() : "未划分";
            long[] arr = agg.computeIfAbsent(region, k -> new long[3]);
            List<Student> students = studentRepo.findBySchoolIdOrderByIdAsc(sch.getId());
            arr[0] += students.size();
            arr[1] += students.stream().filter(this::isAbnormal).count();
            arr[2] += students.stream().filter(this::hasVisited).count();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        agg.forEach((region, arr) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("region", region);
            row.put("total", arr[0]);
            row.put("abnormal", arr[1]);
            row.put("visited", arr[2]);
            row.put("abnormalRate", rate(arr[1], arr[0]));
            row.put("realVisitRate", rate(arr[2], arr[1]));
            out.add(row);
        });
        return out;
    }

    /** 异常事项分布（8 类异常场景的待处理/已闭环） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> issueDistribution() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScreenFlag f : ScreenFlag.values()) {
            long open = ((Number) em.createQuery(
                            "select count(t) from IssueTracker t where t.issueType = :t and t.status = 'OPEN'")
                    .setParameter("t", f.name()).getSingleResult()).longValue();
            long resolved = ((Number) em.createQuery(
                            "select count(t) from IssueTracker t where t.issueType = :t and t.status = 'RESOLVED'")
                    .setParameter("t", f.name()).getSingleResult()).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", f.name());
            row.put("label", f.getLabel());
            row.put("open", open);
            row.put("resolved", resolved);
            out.add(row);
        }
        return out;
    }

    /** 阶段漏斗 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> stageFunnel(Long schoolId, Long regionId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();
        List<Student> students = studentRepo.search(schoolId, null, regionId, null, null, false);
        Map<StudentStage, Long> counts = new LinkedHashMap<>();
        for (StudentStage st : StudentStage.values()) counts.put(st, 0L);
        students.forEach(s -> counts.merge(s.getStage(), 1L, Long::sum));
        List<Map<String, Object>> out = new ArrayList<>();
        counts.forEach((st, c) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stage", st.name());
            row.put("label", st.getLabel());
            row.put("count", c);
            out.add(row);
        });
        return out;
    }
}
