package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 配镜不适调整与适应回访记录。
 * 学生反馈头晕、看不清或镜架不合适时，门店须上传调整结果；
 * 平台保留售后记录，并继续提醒门店/学校做复查提醒与戴镜适应回访。
 * 同一副眼镜可有多次反馈—调整—回访记录。
 */
@Getter
@Setter
@Entity
@Table(name = "comfort_followup")
public class ComfortFollowup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GlassesRecord glasses;

    /** 反馈类型：DIZZY 头晕 / BLURRY 看不清 / FRAME_FIT 镜架不合适 / OTHER */
    @Column(length = 20)
    private String discomfortType;

    private LocalDate feedbackDate;

    /** 学生/家长具体反馈 */
    @Column(length = 500)
    private String feedback;

    /** 门店/医疗机构调整处理结果（重新对光、调整镜架、返修镜片等） */
    @Column(length = 500)
    private String adjustResult;

    private LocalDate adjustDate;

    /** 门店/医生是否建议返院复查 */
    private Boolean reviewAdvised = false;
    private LocalDate reviewPlannedDate;

    /** 适应回访：计划回访日期（如取镜/调整后 7 天）、实际回访日期与结果 */
    private LocalDate callbackDueDate;
    private LocalDate callbackDate;
    @Column(length = 500)
    private String callbackResult;

    /** PLANNED 待回访 / DONE 已完成适应回访 */
    @Column(length = 20)
    private String callbackStatus = "PLANNED";

    /** 调整后是否仍有不适（false=已适应/解决） */
    private Boolean resolved = false;

    private String handler;
}
