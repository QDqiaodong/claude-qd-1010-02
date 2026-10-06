package com.museum.collection.common;

import java.util.List;
import java.util.Map;

/**
 * 博物馆业务词典与规则参数表：藏品状态、展览状态流转、展厅状态、
 * 导览状态流转、会员等级、以及入藏年份 / 展览日期 / 展厅容量等各类阈值。
 *
 * <p>规则集中在此，便于出题时被要求调整或扩展。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 藏品 ----------------
    /** 全部藏品状态：在展 / 库房 / 借展 / 出展中 */
    public static final List<String> ARTIFACT_STATUSES = List.of("在展", "库房", "借展", "出展中");

    /** 可自由流转的藏品状态；「出展中」只能由出展交接流程驱动，不允许手工改 */
    public static final List<String> ARTIFACT_FREE_STATUSES = List.of("在展", "库房", "借展");

    // ---------------- 展览 ----------------
    /** 筹展 → 展出中 → 已闭幕，逐级推进，不可跳级 */
    public static final List<String> EXHIBITION_FLOW = List.of("筹展", "展出中", "已闭幕");

    public static final List<String> EXHIBITION_STATUSES = List.of("筹展", "展出中", "已闭幕");

    // ---------------- 出展交接 ----------------
    /** 草稿 → 待接收 → 已接收 → 已收口；待接收可 → 已拒收；展览提前闭幕时未接收单据 → 已终止 */
    public static final List<String> HANDOVER_STATUSES = List.of("草稿", "待接收", "已接收", "已拒收", "已收口", "已终止");

    /** 占用藏品的交接状态：同一藏品同一时间只允许一张（确认交出即占用，返库收口或拒收才释放） */
    public static final List<String> HANDOVER_OCCUPYING = List.of("待接收", "已接收");

    // ---------------- 展厅 ----------------
    /** 开放 / 维护 / 关闭，可自由切换 */
    public static final List<String> GALLERY_STATUSES = List.of("开放", "维护", "关闭");

    // ---------------- 导览 ----------------
    /** 待开始 → 进行中 → 已完成，逐级推进 */
    public static final List<String> TOUR_FLOW = List.of("待开始", "进行中", "已完成");

    public static final List<String> TOUR_STATUSES = List.of("待开始", "进行中", "已完成");

    // ---------------- 会员 ----------------
    public static final List<String> MEMBER_LEVELS = List.of("普通", "银卡", "金卡");

    /** 会员累计积分对应等级阈值（达到即升级） */
    public static final int SILVER_POINTS = 2000;

    public static final int GOLD_POINTS = 6000;

    // ---------------- 积分调整申请 ----------------
    /** 待复核 → 已通过 / 已驳回 / 已撤回（后三者为终态，不可再处理） */
    public static final List<String> POINT_ADJUST_STATUSES = List.of("待复核", "已通过", "已驳回", "已撤回");

    /** 申请待复核状态：仅该状态可撤回 / 修正 / 复核 */
    public static final String POINT_ADJUST_PENDING = "待复核";

    // ---------------- 取值方法 ----------------

    public static boolean isValidArtifactStatus(String status) {
        return status != null && ARTIFACT_STATUSES.contains(status);
    }

    /** 藏品状态是否允许手工自由流转（出展中不在其内，必须走交接单） */
    public static boolean canFreelyTransferArtifact(String status) {
        return status != null && ARTIFACT_FREE_STATUSES.contains(status);
    }

    /** 展览状态只能逐级推进：筹展 → 展出中 → 已闭幕 */
    public static boolean canTransferExhibition(String from, String to) {
        int i = EXHIBITION_FLOW.indexOf(from);
        return i >= 0 && i + 1 < EXHIBITION_FLOW.size() && EXHIBITION_FLOW.get(i + 1).equals(to);
    }

    public static boolean isValidGalleryStatus(String status) {
        return status != null && GALLERY_STATUSES.contains(status);
    }

    /** 导览状态只能逐级推进：待开始 → 进行中 → 已完成 */
    public static boolean canTransferTour(String from, String to) {
        int i = TOUR_FLOW.indexOf(from);
        return i >= 0 && i + 1 < TOUR_FLOW.size() && TOUR_FLOW.get(i + 1).equals(to);
    }

    public static boolean isValidTourStatus(String status) {
        return status != null && TOUR_STATUSES.contains(status);
    }

    public static boolean isValidMemberLevel(String level) {
        return level != null && MEMBER_LEVELS.contains(level);
    }

    /** 按累计积分自动判定会员等级 */
    public static String decideMemberLevel(int points) {
        if (points >= GOLD_POINTS) {
            return "金卡";
        }
        if (points >= SILVER_POINTS) {
            return "银卡";
        }
        return "普通";
    }
}
