package com.museum.collection.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Member;
import com.museum.collection.entity.PointAdjustment;
import com.museum.collection.repository.MemberRepository;
import com.museum.collection.repository.PointAdjustmentRepository;

/**
 * 会员积分调整申请与复核。
 *
 * <p>状态机：待复核 → 已通过 / 已驳回 / 已撤回（后三者为终态，不可再处理）。
 *
 * <p>核心规则：① 创建申请只落「待复核」记录，绝不动会员积分；
 * ② 待复核期间申请人本人可撤回，也可修正调整值 / 原因 / 凭据后重新提交复核；
 * ③ 复核通过时才在同一事务内更新会员积分并按阈值重算等级，同时落前后积分，
 * 审批记录与会员余额不会不一致；扣减后将为负数时本次复核被拒绝、事务回滚，
 * 申请保持「待复核」，可修正后再次提交；
 * ④ 并发复核同一申请时，申请行悲观锁让后到者排队，拿到锁时看到的是首次复核
 * 已提交的终态，得到明确的已处理结果，不会重复增减积分，也不会覆盖首次复核意见。</p>
 */
@Service
public class PointAdjustmentService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PointAdjustmentRepository adjustmentRepository;
    private final MemberRepository memberRepository;

    public PointAdjustmentService(PointAdjustmentRepository adjustmentRepository,
                                  MemberRepository memberRepository) {
        this.adjustmentRepository = adjustmentRepository;
        this.memberRepository = memberRepository;
    }

    // ---------------- 查询 ----------------

    public List<PointAdjustment> list(String status, Long memberId) {
        if (memberId != null) {
            if (status != null && !status.isBlank()) {
                return adjustmentRepository.findByMemberIdAndStatusOrderByIdDesc(memberId, status);
            }
            return adjustmentRepository.findByMemberIdOrderByIdDesc(memberId);
        }
        if (status != null && !status.isBlank()) {
            return adjustmentRepository.findByStatusOrderByIdDesc(status);
        }
        return adjustmentRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public PointAdjustment get(Long id) {
        return adjustmentRepository.findById(id)
                .orElseThrow(() -> new BizException("积分调整申请不存在：" + id));
    }

    // ---------------- 申请 ----------------

    private String nextApplyNo() {
        int year = LocalDateTime.now().getYear();
        int maxSeq = 0;
        Pattern p = Pattern.compile("^PA-" + year + "-(\\d+)$");
        for (PointAdjustment pa : adjustmentRepository.findAll()) {
            if (pa.getApplyNo() == null) {
                continue;
            }
            Matcher m = p.matcher(pa.getApplyNo());
            if (m.find()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(m.group(1)));
            }
        }
        return String.format("PA-%d-%03d", year, maxSeq + 1);
    }

    /**
     * 提交调整申请：只落「待复核」记录，不触碰会员积分；
     * 积分只有在复核通过的那一刻才会变动。
     */
    @Transactional
    public PointAdjustment create(PointAdjustment input) {
        if (input.getMemberId() == null) {
            throw new BizException("必须指定要调整积分的会员");
        }
        Member member = memberRepository.findById(input.getMemberId())
                .orElseThrow(() -> new BizException("会员不存在：" + input.getMemberId()));
        validateDelta(input.getDelta());
        if (isBlank(input.getReason())) {
            throw new BizException("业务原因不能为空");
        }
        if (isBlank(input.getVoucher())) {
            throw new BizException("关联凭据不能为空");
        }
        if (isBlank(input.getApplicant())) {
            throw new BizException("申请人不能为空");
        }

        PointAdjustment pa = new PointAdjustment();
        pa.setApplyNo(nextApplyNo());
        pa.setMemberId(member.getId());
        pa.setMemberNo(member.getMemberNo());
        pa.setMemberName(member.getName());
        pa.setDelta(input.getDelta());
        pa.setReason(input.getReason().trim());
        pa.setVoucher(input.getVoucher().trim());
        pa.setApplicant(input.getApplicant().trim());
        pa.setStatus(Dict.POINT_ADJUST_PENDING);
        pa.setCreatedAt(LocalDateTime.now());
        return adjustmentRepository.save(pa);
    }

    /**
     * 修正待复核申请（调整值 / 原因 / 凭据）：复核时发现问题的申请保持待复核，
     * 申请人修正后复核人可再次复核；已进入终态的申请不可再改。
     */
    @Transactional
    public PointAdjustment update(Long id, PointAdjustment input) {
        PointAdjustment pa = lockGet(id);
        if (!Dict.POINT_ADJUST_PENDING.equals(pa.getStatus())) {
            throw alreadyProcessed(pa, "修正");
        }
        if (input.getDelta() != null) {
            validateDelta(input.getDelta());
            pa.setDelta(input.getDelta());
        }
        if (input.getReason() != null) {
            if (input.getReason().isBlank()) {
                throw new BizException("业务原因不能为空");
            }
            pa.setReason(input.getReason().trim());
        }
        if (input.getVoucher() != null) {
            if (input.getVoucher().isBlank()) {
                throw new BizException("关联凭据不能为空");
            }
            pa.setVoucher(input.getVoucher().trim());
        }
        return adjustmentRepository.save(pa);
    }

    /** 撤回：仅待复核可撤回，且只能由申请人本人操作；撤回为终态 */
    @Transactional
    public PointAdjustment withdraw(Long id, String operator) {
        PointAdjustment pa = lockGet(id);
        if (!Dict.POINT_ADJUST_PENDING.equals(pa.getStatus())) {
            throw alreadyProcessed(pa, "撤回");
        }
        if (isBlank(operator)) {
            throw new BizException("撤回人不能为空");
        }
        if (!operator.trim().equals(pa.getApplicant())) {
            throw new BizException("只有申请人「" + pa.getApplicant() + "」本人才能撤回该申请");
        }
        pa.setStatus("已撤回");
        pa.setWithdrawnAt(LocalDateTime.now());
        return adjustmentRepository.save(pa);
    }

    // ---------------- 复核 ----------------

    /**
     * 复核通过：待复核 → 已通过，并在同一事务内更新会员积分、按阈值重算等级、落前后积分。
     *
     * <p>并发口径：先锁申请行、再锁会员行（与 reject / withdraw 同序，避免死锁）。
     * 两名复核人同时复核同一申请时，后到者阻塞在申请行锁上，拿到锁时看到的是首次
     * 复核已提交的终态，于是得到明确的已处理结果，本次复核不生效，积分不会重复增减。
     *
     * <p>扣减后将为负数时抛业务异常，整个事务回滚：积分不动、申请保持「待复核」，
     * 申请人可修正调整值后重新提交复核。</p>
     */
    @Transactional
    public PointAdjustment approve(Long id, String reviewer, String comment) {
        if (isBlank(reviewer)) {
            throw new BizException("复核人不能为空");
        }
        PointAdjustment pa = lockGet(id);
        if (!Dict.POINT_ADJUST_PENDING.equals(pa.getStatus())) {
            throw alreadyProcessed(pa, "复核");
        }
        Member member = lockMember(pa.getMemberId());
        int before = member.getPoints();
        int after = before + pa.getDelta();
        if (after < 0) {
            throw new BizException("复核未生效：会员「" + member.getName() + "」当前积分 " + before
                    + "，扣减 " + (-pa.getDelta()) + " 后将为负数；申请仍保持「待复核」，请修正调整值后重新提交复核");
        }
        pa.setPointsBefore(before);
        member.setPoints(after);
        member.setLevel(Dict.decideMemberLevel(after));
        memberRepository.save(member);
        pa.setPointsAfter(after);
        pa.setStatus("已通过");
        pa.setReviewer(reviewer.trim());
        pa.setReviewComment(trim(comment));
        pa.setReviewedAt(LocalDateTime.now());
        return adjustmentRepository.save(pa);
    }

    /** 复核驳回：待复核 → 已驳回（终态留痕），必须填复核意见；不动会员积分 */
    @Transactional
    public PointAdjustment reject(Long id, String reviewer, String comment) {
        if (isBlank(reviewer)) {
            throw new BizException("复核人不能为空");
        }
        PointAdjustment pa = lockGet(id);
        if (!Dict.POINT_ADJUST_PENDING.equals(pa.getStatus())) {
            throw alreadyProcessed(pa, "复核");
        }
        if (isBlank(comment)) {
            throw new BizException("驳回必须填写复核意见");
        }
        pa.setStatus("已驳回");
        pa.setReviewer(reviewer.trim());
        pa.setReviewComment(comment.trim());
        pa.setReviewedAt(LocalDateTime.now());
        return adjustmentRepository.save(pa);
    }

    // ---------------- 内部 ----------------

    private PointAdjustment lockGet(Long id) {
        return adjustmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("积分调整申请不存在：" + id));
    }

    private Member lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + memberId));
    }

    /** 终态申请的统一提示：给出明确的已处理结果（处理人 / 时间 / 结论），本次操作不生效 */
    private BizException alreadyProcessed(PointAdjustment pa, String action) {
        if ("已撤回".equals(pa.getStatus())) {
            return new BizException("申请「" + pa.getApplyNo() + "」已被申请人「" + pa.getApplicant()
                    + "」撤回，不能" + action + "；如需调整请重新提交申请");
        }
        return new BizException("申请「" + pa.getApplyNo() + "」已由复核人「" + pa.getReviewer() + "」于 "
                + pa.getReviewedAt().format(FMT) + " 处理为「" + pa.getStatus() + "」，本次" + action
                + "未生效，不会重复增减积分");
    }

    private static void validateDelta(Integer delta) {
        if (delta == null) {
            throw new BizException("调整值不能为空（正数加分、负数扣分）");
        }
        if (delta == 0) {
            throw new BizException("调整值不能为 0（正数加分、负数扣分）");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
