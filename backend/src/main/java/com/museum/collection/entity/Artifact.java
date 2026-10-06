package com.museum.collection.entity;

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
 * 藏品（文物）。
 *
 * <p>状态：在展 / 库房 / 借展。特征明细 attrs 用集合表承载数组语义。</p>
 */
@Entity
@Table(name = "artifact")
@Getter
@Setter
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 藏品编号，唯一，如 WB-0001 */
    @Column(name = "artifact_no", nullable = false, unique = true, length = 32)
    private String artifactNo;

    /** 名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 朝代 / 年代（大类），如「唐」 */
    @Column(length = 32)
    private String dynasty;

    /** 入藏年份 */
    @Column(name = "acquire_year")
    private Integer acquireYear;

    /** 在展 / 库房 / 借展 */
    @Column(length = 16)
    private String status;

    /** 特征明细：年代 + 材质，多条 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artifact_attrs", joinColumns = @JoinColumn(name = "artifact_id"))
    @OrderColumn(name = "attr_idx")
    private List<ArtifactAttr> attrs = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
