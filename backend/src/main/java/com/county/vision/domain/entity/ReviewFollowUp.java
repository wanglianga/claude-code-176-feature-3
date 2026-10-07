package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 复查随访（按医生建议周期；半年复查未到场也在此登记） */
@Getter
@Setter
@Entity
@Table(name = "review_followup")
public class ReviewFollowUp extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    private LocalDate plannedDate;
    private LocalDate actualDate;

    /** PLANNED 待复查 / DONE 已完成 / NO_SHOW 未到场 */
    private String status;

    /** REVIEW 周期复查 / ADAPT 配镜适应回访 */
    private String kind = "REVIEW";

    private String orgName;

    /** 本次视力结果 */
    private String visionResult;

    @Column(length = 500)
    private String remark;

    /** 未到场催办次数 */
    private Integer remindCount = 0;
}
