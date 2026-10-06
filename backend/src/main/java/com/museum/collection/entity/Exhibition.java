package com.museum.collection.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * 展览。
 *
 * <p>状态：筹展 → 展出中 → 已闭幕（逐级推进）。亮点 highlights 用集合表存字符串数组。</p>
 */
@Entity
@Table(name = "exhibition")
@Getter
@Setter
public class Exhibition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 展览编号，唯一，如 EX-2026-01 */
    @Column(name = "ex_code", nullable = false, unique = true, length = 32)
    private String exCode;

    /** 标题 */
    @Column(nullable = false, length = 128)
    private String title;

    /** 开始日期 */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 结束日期 */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 筹展 / 展出中 / 已闭幕 */
    @Column(length = 16)
    private String status;

    /** 展览亮点（字符串数组语义） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exhibition_highlights", joinColumns = @JoinColumn(name = "ex_id"))
    @OrderColumn(name = "hl_idx")
    @Column(name = "highlight", length = 128)
    private List<String> highlights = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
