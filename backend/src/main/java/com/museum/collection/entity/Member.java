package com.museum.collection.entity;

import java.time.LocalDate;
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
 * 会员。
 *
 * <p>等级：普通 / 银卡 / 金卡，按累计积分自动判定。points 为累计积分。</p>
 */
@Entity
@Table(name = "member")
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会员编号，唯一，如 MB-0001 */
    @Column(name = "member_no", nullable = false, unique = true, length = 32)
    private String memberNo;

    /** 姓名 */
    @Column(nullable = false, length = 32)
    private String name;

    /** 手机号 */
    @Column(length = 24)
    private String phone;

    /** 普通 / 银卡 / 金卡 */
    @Column(length = 16)
    private String level;

    /** 入会日期 */
    @Column(name = "joined_at")
    private LocalDate joinedAt;

    /** 累计积分 */
    @Column(nullable = false)
    private Integer points = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
