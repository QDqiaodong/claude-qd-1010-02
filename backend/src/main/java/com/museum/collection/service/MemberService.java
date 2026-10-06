package com.museum.collection.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Member;
import com.museum.collection.repository.MemberRepository;

/**
 * 会员业务。
 *
 * <p>核心规则：① 等级须在 普通 / 银卡 / 金卡 之内；② 累计积分变化后按阈值自动重判等级。</p>
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> list(String level) {
        if (level != null && !level.isBlank()) {
            return memberRepository.findByLevel(level);
        }
        return memberRepository.findAll();
    }

    public Member get(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BizException("会员不存在：" + id));
    }

    @Transactional
    public Member create(Member input) {
        if (input.getMemberNo() == null || input.getMemberNo().isBlank()) {
            throw new BizException("会员编号不能为空");
        }
        memberRepository.findByMemberNo(input.getMemberNo().trim()).ifPresent(m -> {
            throw new BizException("会员编号已存在：" + input.getMemberNo());
        });
        if (input.getLevel() == null || input.getLevel().isBlank()) {
            input.setLevel("普通");
        } else if (!Dict.isValidMemberLevel(input.getLevel())) {
            throw new BizException("会员等级非法：" + input.getLevel());
        }
        if (input.getPoints() == null) {
            input.setPoints(0);
        }
        input.setLevel(Dict.decideMemberLevel(input.getPoints()));
        if (input.getJoinedAt() == null) {
            input.setJoinedAt(LocalDate.now());
        }
        input.setCreatedAt(LocalDateTime.now());
        return memberRepository.save(input);
    }

    @Transactional
    public Member update(Long id, Member input) {
        Member existing = get(id);
        if (input.getMemberNo() != null && !input.getMemberNo().equals(existing.getMemberNo())) {
            memberRepository.findByMemberNo(input.getMemberNo().trim()).ifPresent(m -> {
                throw new BizException("会员编号已存在：" + input.getMemberNo());
            });
            existing.setMemberNo(input.getMemberNo().trim());
        }
        if (input.getName() != null) {
            existing.setName(input.getName());
        }
        if (input.getPhone() != null) {
            existing.setPhone(input.getPhone());
        }
        if (input.getJoinedAt() != null) {
            existing.setJoinedAt(input.getJoinedAt());
        }
        if (input.getPoints() != null) {
            existing.setPoints(input.getPoints());
            existing.setLevel(Dict.decideMemberLevel(input.getPoints()));
        }
        if (input.getLevel() != null && !input.getLevel().isBlank()
                && !existing.getLevel().equals(input.getLevel())) {
            if (!Dict.isValidMemberLevel(input.getLevel())) {
                throw new BizException("会员等级非法：" + input.getLevel());
            }
            existing.setLevel(input.getLevel());
        }
        return memberRepository.save(existing);
    }

    @Transactional
    public Member addPoints(Long id, int delta) {
        // 锁会员行：与「积分调整复核通过」互斥，避免并发变动互相覆盖导致余额与审批记录不一致
        Member existing = memberRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("会员不存在：" + id));
        int newPoints = existing.getPoints() + delta;
        if (newPoints < 0) {
            throw new BizException("积分扣减后不能为负");
        }
        existing.setPoints(newPoints);
        existing.setLevel(Dict.decideMemberLevel(newPoints));
        return memberRepository.save(existing);
    }
}
