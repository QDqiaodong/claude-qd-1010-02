package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Artifact;
import com.museum.collection.entity.Exhibition;
import com.museum.collection.entity.Handover;
import com.museum.collection.repository.ArtifactRepository;
import com.museum.collection.repository.ExhibitionRepository;
import com.museum.collection.repository.HandoverRepository;

/**
 * 藏品出展交接业务。
 *
 * <p>状态机：草稿 → 待接收 → 已接收 → 已收口；待接收可 → 已拒收；
 * 展览提前闭幕时，尚未接收的草稿 / 待接收单据 → 已终止（终态，不再流转）。
 *
 * <p>核心规则：① 交接单从「库房」藏品发起、关联未闭幕展览，草稿可反复修改；
 * ② 确认交出后藏品进入「出展中」，同一藏品同一时间只允许一张占用中的交接单
 * （藏品行悲观锁 + 数据库唯一约束双保险，后到者得到明确原因且整单回滚，不留半张有效单据）；
 * ③ 拒收必须填差异说明，拒收后藏品回「库房」、单据留痕且不可再恢复流转；
 * ④ 已接收的藏品必须返库确认（记录返库时间 / 经办人 / 返库状况）后藏品才回「库房」、
 * 单据进入「已收口」，此后才允许下一次出展交接。</p>
 */
@Service
public class HandoverService {

    private final HandoverRepository handoverRepository;
    private final ArtifactRepository artifactRepository;
    private final ExhibitionRepository exhibitionRepository;

    public HandoverService(HandoverRepository handoverRepository,
                           ArtifactRepository artifactRepository,
                           ExhibitionRepository exhibitionRepository) {
        this.handoverRepository = handoverRepository;
        this.artifactRepository = artifactRepository;
        this.exhibitionRepository = exhibitionRepository;
    }

    // ---------------- 查询 ----------------

    public List<Handover> list(String status, Long artifactId, Long exhibitionId) {
        List<Handover> base;
        if (artifactId != null) {
            base = handoverRepository.findByArtifactIdOrderByIdDesc(artifactId);
        } else if (exhibitionId != null) {
            base = handoverRepository.findByExhibitionIdOrderByIdDesc(exhibitionId);
        } else if (status != null && !status.isBlank()) {
            base = handoverRepository.findByStatusOrderByIdDesc(status);
        } else {
            base = handoverRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        }
        // 组合过滤：按藏品 / 展览定位的同时再按状态细分
        if (status != null && !status.isBlank() && (artifactId != null || exhibitionId != null)) {
            base = base.stream().filter(h -> status.equals(h.getStatus())).toList();
        }
        return base;
    }

    public Handover get(Long id) {
        return handoverRepository.findById(id)
                .orElseThrow(() -> new BizException("交接单不存在：" + id));
    }

    /** 藏品当前占用中的交接单（待接收 / 已接收），没有则返回 null */
    public Handover currentForArtifact(Long artifactId) {
        List<Handover> list = handoverRepository.findByArtifactIdAndStatusIn(artifactId, Dict.HANDOVER_OCCUPYING);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------------- 发起与草稿 ----------------

    private String nextHandoverNo() {
        int year = LocalDateTime.now().getYear();
        int maxSeq = 0;
        Pattern p = Pattern.compile("^JJ-" + year + "-(\\d+)$");
        for (Handover h : handoverRepository.findAll()) {
            if (h.getHandoverNo() == null) {
                continue;
            }
            Matcher m = p.matcher(h.getHandoverNo());
            if (m.find()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(m.group(1)));
            }
        }
        return String.format("JJ-%d-%03d", year, maxSeq + 1);
    }

    @Transactional
    public Handover create(Handover input) {
        if (input.getArtifactId() == null) {
            throw new BizException("必须指定要出展的藏品");
        }
        Artifact artifact = artifactRepository.findById(input.getArtifactId())
                .orElseThrow(() -> new BizException("藏品不存在：" + input.getArtifactId()));
        if (!"库房".equals(artifact.getStatus())) {
            throw new BizException("只有「库房」状态的藏品才能发起出展交接，藏品「" + artifact.getName()
                    + "」当前为「" + artifact.getStatus() + "」");
        }
        Exhibition ex = loadOpenExhibition(input.getExhibitionId());

        Handover h = new Handover();
        h.setHandoverNo(nextHandoverNo());
        h.setArtifactId(artifact.getId());
        h.setArtifactNo(artifact.getArtifactNo());
        h.setArtifactName(artifact.getName());
        h.setExhibitionId(ex.getId());
        h.setExCode(ex.getExCode());
        h.setExTitle(ex.getTitle());
        h.setDeliverer(trim(input.getDeliverer()));
        h.setReceiver(trim(input.getReceiver()));
        h.setHandoverTime(input.getHandoverTime());
        h.setPreCondition(trim(input.getPreCondition()));
        h.setNote(trim(input.getNote()));
        h.setStatus("草稿");
        h.setCreatedAt(LocalDateTime.now());
        return handoverRepository.save(h);
    }

    /** 草稿可反复修改；一旦进入流转（待接收及以后）即不可再改 */
    @Transactional
    public Handover update(Long id, Handover input) {
        Handover h = lockGet(id);
        if (!"草稿".equals(h.getStatus())) {
            throw new BizException("交接单当前为「" + h.getStatus() + "」，只有草稿才能修改");
        }
        if (input.getExhibitionId() != null && !input.getExhibitionId().equals(h.getExhibitionId())) {
            Exhibition ex = loadOpenExhibition(input.getExhibitionId());
            h.setExhibitionId(ex.getId());
            h.setExCode(ex.getExCode());
            h.setExTitle(ex.getTitle());
        }
        if (input.getDeliverer() != null) {
            h.setDeliverer(trim(input.getDeliverer()));
        }
        if (input.getReceiver() != null) {
            h.setReceiver(trim(input.getReceiver()));
        }
        if (input.getHandoverTime() != null) {
            h.setHandoverTime(input.getHandoverTime());
        }
        if (input.getPreCondition() != null) {
            h.setPreCondition(trim(input.getPreCondition()));
        }
        if (input.getNote() != null) {
            h.setNote(trim(input.getNote()));
        }
        return handoverRepository.save(h);
    }

    // ---------------- 流转 ----------------

    /**
     * 确认交出：草稿 → 待接收，藏品 → 出展中。
     *
     * <p>并发口径：先锁单据行、再锁藏品行（与 terminateForExhibition 同序，避免死锁）。
     * 两名保管员同时确认同一藏品时，后到者阻塞在藏品行锁上，拿到锁时看到的是对方
     * 已提交的「出展中」，于是得到明确失败原因；整个事务回滚，不留半张有效单据。</p>
     */
    @Transactional
    public Handover confirm(Long id) {
        Handover h = lockGet(id);
        if (!"草稿".equals(h.getStatus())) {
            throw new BizException("交接单当前为「" + h.getStatus() + "」，只有草稿才能确认交出");
        }
        if (isBlank(h.getDeliverer()) || isBlank(h.getReceiver()) || isBlank(h.getPreCondition())) {
            throw new BizException("交接单信息不完整：交出人、接收人、出展前状况均为必填，请先补全草稿");
        }
        Artifact artifact = lockArtifact(h.getArtifactId());
        Exhibition ex = exhibitionRepository.findById(h.getExhibitionId())
                .orElseThrow(() -> new BizException("展览不存在：" + h.getExhibitionId()));
        if ("已闭幕".equals(ex.getStatus())) {
            throw new BizException("展览「" + ex.getTitle() + "」已闭幕，交接单已停止流转，不能确认交出");
        }
        if (!"库房".equals(artifact.getStatus())) {
            Handover occupying = currentForArtifact(artifact.getId());
            String occ = occupying != null
                    ? "（交接单「" + occupying.getHandoverNo() + "」" + occupying.getStatus() + "）" : "";
            throw new BizException("藏品「" + artifact.getName() + "」当前状态为「" + artifact.getStatus() + "」" + occ
                    + "，不在库房，本次确认未生效；须待上一张交接单返库收口后才能再次确认交出");
        }
        h.setStatus("待接收");
        if (h.getHandoverTime() == null) {
            h.setHandoverTime(LocalDateTime.now());
        }
        artifact.setStatus("出展中");
        artifactRepository.save(artifact);
        try {
            return handoverRepository.saveAndFlush(h);
        } catch (DataIntegrityViolationException e) {
            // 双保险：数据库对「同一藏品仅一张占用中交接单」有唯一约束，撞约束即已被他人抢占
            throw new BizException("藏品「" + artifact.getName() + "」已存在进行中的交接单，本次确认未生效");
        }
    }

    /** 确认接收：待接收 → 已接收（藏品保持出展中） */
    @Transactional
    public Handover receive(Long id) {
        Handover h = lockGet(id);
        if (!"待接收".equals(h.getStatus())) {
            throw new BizException("交接单当前为「" + h.getStatus() + "」，只有待接收的交接单才能确认接收");
        }
        h.setStatus("已接收");
        h.setReceivedAt(LocalDateTime.now());
        return handoverRepository.save(h);
    }

    /** 拒收：待接收 → 已拒收（终态留痕），藏品回「库房」 */
    @Transactional
    public Handover reject(Long id, String reason) {
        Handover h = lockGet(id);
        if (!"待接收".equals(h.getStatus())) {
            throw new BizException("交接单当前为「" + h.getStatus() + "」，只有待接收的交接单才能拒收");
        }
        if (isBlank(reason)) {
            throw new BizException("拒收必须填写差异说明");
        }
        Artifact artifact = lockArtifact(h.getArtifactId());
        h.setStatus("已拒收");
        h.setRejectReason(reason.trim());
        h.setRejectedAt(LocalDateTime.now());
        artifact.setStatus("库房");
        artifactRepository.save(artifact);
        return handoverRepository.save(h);
    }

    /** 返库确认：已接收 → 已收口（终态），藏品回「库房」，此后才允许下一次出展交接 */
    @Transactional
    public Handover returnBack(Long id, String handler, String condition) {
        Handover h = lockGet(id);
        if (!"已接收".equals(h.getStatus())) {
            throw new BizException("交接单当前为「" + h.getStatus() + "」，只有已接收的交接单才能返库确认");
        }
        if (isBlank(handler)) {
            throw new BizException("返库经办人不能为空");
        }
        if (isBlank(condition)) {
            throw new BizException("返库状况不能为空");
        }
        Artifact artifact = lockArtifact(h.getArtifactId());
        h.setStatus("已收口");
        h.setReturnHandler(handler.trim());
        h.setReturnCondition(condition.trim());
        h.setReturnedAt(LocalDateTime.now());
        artifact.setStatus("库房");
        artifactRepository.save(artifact);
        return handoverRepository.save(h);
    }

    /**
     * 展览闭幕联动：该展览尚未接收的交接单（草稿 / 待接收）停止流转 → 已终止；
     * 其中已确认交出的（待接收）藏品归还「库房」。已接收的不动，仍须走返库确认。
     */
    @Transactional
    public void terminateForExhibition(Long exhibitionId) {
        List<Handover> active = handoverRepository.findByExhibitionIdAndStatusIn(
                exhibitionId, List.of("草稿", "待接收"));
        for (Handover snapshot : active) {
            Handover h = lockGet(snapshot.getId());
            boolean wasPendingReceive = "待接收".equals(h.getStatus());
            if (!wasPendingReceive && !"草稿".equals(h.getStatus())) {
                continue;
            }
            h.setStatus("已终止");
            h.setTerminateReason("展览提前闭幕，尚未接收的交接单停止流转");
            if (wasPendingReceive) {
                Artifact artifact = lockArtifact(h.getArtifactId());
                if ("出展中".equals(artifact.getStatus())) {
                    artifact.setStatus("库房");
                    artifactRepository.save(artifact);
                }
            }
            handoverRepository.save(h);
        }
    }

    // ---------------- 内部 ----------------

    private Handover lockGet(Long id) {
        return handoverRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("交接单不存在：" + id));
    }

    private Artifact lockArtifact(Long artifactId) {
        return artifactRepository.findByIdForUpdate(artifactId)
                .orElseThrow(() -> new BizException("藏品不存在：" + artifactId));
    }

    private Exhibition loadOpenExhibition(Long exhibitionId) {
        if (exhibitionId == null) {
            throw new BizException("必须关联展览");
        }
        Exhibition ex = exhibitionRepository.findById(exhibitionId)
                .orElseThrow(() -> new BizException("展览不存在：" + exhibitionId));
        if ("已闭幕".equals(ex.getStatus())) {
            throw new BizException("展览「" + ex.getTitle() + "」已闭幕，不能关联该展览发起交接");
        }
        return ex;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
