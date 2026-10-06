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
 * 导览（讲解场次）。
 *
 * <p>绑定到某个展厅（galleryId）。状态：待开始 → 进行中 → 已完成。
 * 新增时受展厅容量约束：该展厅已有导览数不得超过其 capacity。</p>
 */
@Entity
@Table(name = "tour")
@Getter
@Setter
public class Tour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 导览编号，唯一，如 TO-2026-001 */
    @Column(name = "tour_no", nullable = false, unique = true, length = 32)
    private String tourNo;

    /** 所属展厅 id */
    @Column(name = "gallery_id")
    private Long galleryId;

    /** 所属展厅编号（冗余，便于展示与统计） */
    @Column(name = "gallery_code", length = 32)
    private String galleryCode;

    /** 所属展厅名称（冗余） */
    @Column(name = "gallery_name", length = 64)
    private String galleryName;

    /** 导览主题 */
    @Column(length = 128)
    private String title;

    /** 讲解员 */
    @Column(name = "guide_name", length = 32)
    private String guideName;

    /** 安排时间 */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 待开始 / 进行中 / 已完成 */
    @Column(length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
