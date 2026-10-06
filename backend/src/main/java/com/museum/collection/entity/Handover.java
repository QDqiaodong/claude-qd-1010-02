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
 * 藏品出展交接单。
 *
 * <p>状态机：草稿 → 待接收 → 已接收 → 已收口；待接收可 → 已拒收（留痕，终态）；
 * 展览提前闭幕时，尚未接收的草稿 / 待接收单据 → 已终止（终态）。
 *
 * <p>同一藏品同一时间只允许一张「待接收 / 已接收」交接单（占用中），
 * 由数据库生成列 active_artifact_id 上的唯一约束兜底。</p>
 */
@Entity
@Table(name = "handover")
@Getter
@Setter
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 交接单号，唯一，如 JJ-2026-001 */
    @Column(name = "handover_no", nullable = false, unique = true, length = 32)
    private String handoverNo;

    /** 藏品 id */
    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    /** 藏品编号（冗余，便于两端展示） */
    @Column(name = "artifact_no", length = 32)
    private String artifactNo;

    /** 藏品名称（冗余） */
    @Column(name = "artifact_name", length = 128)
    private String artifactName;

    /** 展览 id */
    @Column(name = "exhibition_id", nullable = false)
    private Long exhibitionId;

    /** 展览编号（冗余） */
    @Column(name = "ex_code", length = 32)
    private String exCode;

    /** 展览标题（冗余） */
    @Column(name = "ex_title", length = 128)
    private String exTitle;

    /** 交出人 */
    @Column(length = 32)
    private String deliverer;

    /** 接收人 */
    @Column(length = 32)
    private String receiver;

    /** 交接时间（确认交出时若为空则取当前时间） */
    @Column(name = "handover_time")
    private LocalDateTime handoverTime;

    /** 出展前状况 */
    @Column(name = "pre_condition", length = 512)
    private String preCondition;

    /** 随附说明 */
    @Column(name = "note", length = 512)
    private String note;

    /** 草稿 / 待接收 / 已接收 / 已拒收 / 已收口 / 已终止 */
    @Column(length = 16)
    private String status;

    /** 接收时间 */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** 拒收差异说明（拒收必填，留痕） */
    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    /** 拒收时间 */
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /** 返库经办人 */
    @Column(name = "return_handler", length = 32)
    private String returnHandler;

    /** 返库状况 */
    @Column(name = "return_condition", length = 512)
    private String returnCondition;

    /** 返库时间 */
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    /** 终止原因（展览提前闭幕时写入） */
    @Column(name = "terminate_reason", length = 256)
    private String terminateReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
