package com.museum.collection.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * 会员积分调整申请单。
 *
 * <p>状态机：待复核 → 已通过 / 已驳回 / 已撤回（后三者为终态，不可再处理）。
 *
 * <p>创建申请不直接改会员积分；仅「复核通过」在同一事务内更新会员积分并按阈值重算等级，
 * 同时落 points_before / points_after，保证审批记录与会员余额一致。
 * 待复核期间申请人可撤回，也可修正调整值 / 原因 / 凭据后重新提交复核。</p>
 */
@Entity
@Table(name = "point_adjustment")
@Getter
@Setter
public class PointAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请单号，唯一，如 PA-2026-001 */
    @Column(name = "apply_no", nullable = false, unique = true, length = 32)
    private String applyNo;

    /** 会员 id */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 会员编号（冗余，便于两端展示） */
    @Column(name = "member_no", length = 32)
    private String memberNo;

    /** 会员姓名（冗余） */
    @Column(name = "member_name", length = 32)
    private String memberName;

    /** 调整值（正数加分、负数扣分，不能为 0） */
    @Column(nullable = false)
    private Integer delta;

    /** 业务原因 */
    @Column(nullable = false, length = 512)
    private String reason;

    /** 关联凭据（活动单号 / 兑换单号等） */
    @Column(nullable = false, length = 128)
    private String voucher;

    /** 申请人 */
    @Column(nullable = false, length = 32)
    private String applicant;

    /** 待复核 / 已通过 / 已驳回 / 已撤回 */
    @Column(nullable = false, length = 16)
    private String status;

    /** 通过前积分（仅已通过时落） */
    @Column(name = "points_before")
    private Integer pointsBefore;

    /** 通过后积分（仅已通过时落） */
    @Column(name = "points_after")
    private Integer pointsAfter;

    /** 复核人 */
    @Column(length = 32)
    private String reviewer;

    /** 复核意见（驳回必填，留痕） */
    @Column(name = "review_comment", length = 512)
    private String reviewComment;

    /** 复核时间 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 撤回时间 */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
