package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Exhibition;
import com.museum.collection.repository.ExhibitionRepository;

/**
 * 展览业务。
 *
 * <p>核心规则：① 结束日期不得早于开始日期；② 状态只能逐级推进：筹展 → 展出中 → 已闭幕；
 * ③ 展览闭幕时联动出展交接：尚未接收的交接单停止流转（已接收的仍须返库确认）。</p>
 */
@Service
public class ExhibitionService {

    private final ExhibitionRepository exhibitionRepository;
    private final HandoverService handoverService;

    public ExhibitionService(ExhibitionRepository exhibitionRepository, HandoverService handoverService) {
        this.exhibitionRepository = exhibitionRepository;
        this.handoverService = handoverService;
    }

    public List<Exhibition> list(String status) {
        if (status != null && !status.isBlank()) {
            return exhibitionRepository.findByStatus(status);
        }
        return exhibitionRepository.findAll();
    }

    public Exhibition get(Long id) {
        return exhibitionRepository.findById(id)
                .orElseThrow(() -> new BizException("展览不存在：" + id));
    }

    @Transactional
    public Exhibition create(Exhibition input) {
        if (input.getExCode() == null || input.getExCode().isBlank()) {
            throw new BizException("展览编号不能为空");
        }
        exhibitionRepository.findByExCode(input.getExCode().trim()).ifPresent(e -> {
            throw new BizException("展览编号已存在：" + input.getExCode());
        });
        if (input.getStartDate() != null && input.getEndDate() != null
                && input.getEndDate().isBefore(input.getStartDate())) {
            throw new BizException("展览结束日期不能早于开始日期");
        }
        if (input.getStatus() == null || input.getStatus().isBlank()) {
            input.setStatus("筹展");
        } else if (!Dict.EXHIBITION_STATUSES.contains(input.getStatus())) {
            throw new BizException("展览状态非法：" + input.getStatus());
        }
        input.setCreatedAt(LocalDateTime.now());
        return exhibitionRepository.save(input);
    }

    @Transactional
    public Exhibition update(Long id, Exhibition input) {
        Exhibition existing = get(id);
        String oldStatus = existing.getStatus();
        if (input.getExCode() != null && !input.getExCode().equals(existing.getExCode())) {
            exhibitionRepository.findByExCode(input.getExCode().trim()).ifPresent(e -> {
                throw new BizException("展览编号已存在：" + input.getExCode());
            });
            existing.setExCode(input.getExCode().trim());
        }
        if (input.getTitle() != null) {
            existing.setTitle(input.getTitle());
        }
        if (input.getStartDate() != null) {
            existing.setStartDate(input.getStartDate());
        }
        if (input.getEndDate() != null) {
            existing.setEndDate(input.getEndDate());
        }
        if (existing.getStartDate() != null && existing.getEndDate() != null
                && existing.getEndDate().isBefore(existing.getStartDate())) {
            throw new BizException("展览结束日期不能早于开始日期");
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            if (!Dict.EXHIBITION_STATUSES.contains(input.getStatus())) {
                throw new BizException("展览状态非法：" + input.getStatus());
            }
            existing.setStatus(input.getStatus());
        }
        if (input.getHighlights() != null) {
            existing.setHighlights(input.getHighlights());
        }
        Exhibition saved = exhibitionRepository.save(existing);
        if ("已闭幕".equals(saved.getStatus()) && !"已闭幕".equals(oldStatus)) {
            // 展览闭幕联动：尚未接收的交接单停止流转（已接收的仍须返库确认）
            handoverService.terminateForExhibition(id);
        }
        return saved;
    }

    @Transactional
    public Exhibition transfer(Long id, String target) {
        Exhibition existing = get(id);
        if (!Dict.EXHIBITION_STATUSES.contains(target)) {
            throw new BizException("展览状态非法：" + target);
        }
        if (!Dict.canTransferExhibition(existing.getStatus(), target)) {
            throw new BizException("展览状态不可从「" + existing.getStatus() + "」跳转到「" + target + "」");
        }
        existing.setStatus(target);
        Exhibition saved = exhibitionRepository.save(existing);
        if ("已闭幕".equals(target)) {
            // 展览闭幕联动：尚未接收的交接单停止流转（已接收的仍须返库确认）
            handoverService.terminateForExhibition(id);
        }
        return saved;
    }
}
