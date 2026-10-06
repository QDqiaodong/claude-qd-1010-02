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
 * 展厅。
 *
 * <p>状态：开放 / 维护 / 关闭。capacity 为该展厅可承载的并发导览场次上限。</p>
 */
@Entity
@Table(name = "gallery")
@Getter
@Setter
public class Gallery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 展厅编号，唯一，如 GL-01 */
    @Column(name = "gallery_code", nullable = false, unique = true, length = 32)
    private String galleryCode;

    /** 名称 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 可承载导览场次上限 */
    @Column(nullable = false)
    private Integer capacity;

    /** 开放 / 维护 / 关闭 */
    @Column(length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
