package com.museum.collection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 藏品特征明细（嵌入值对象）。
 *
 * <p>MySQL 8 无原生 SQL ARRAY，故用 {@code @ElementCollection + @CollectionTable}
 * 承载「数组」语义：一件藏品可挂多条特征，每条特征含 年代(era) 与 材质(material)。</p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactAttr {

    /** 年代 / 时期细分，如「贞观」「唐晚期」 */
    @Column(name = "era", length = 32)
    private String era;

    /** 材质，如「鎏金铜」「灰陶」 */
    @Column(name = "material", length = 32)
    private String material;
}
