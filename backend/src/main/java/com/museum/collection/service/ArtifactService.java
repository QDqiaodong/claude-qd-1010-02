package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Artifact;
import com.museum.collection.repository.ArtifactRepository;

/**
 * 藏品业务。
 *
 * <p>核心规则：① 入藏年份不能晚于当前年份；② 在展 / 库房 / 借展 三者可自由流转，
 * 「出展中」只能由出展交接流程驱动（确认交出进入、拒收 / 返库收口退出），不允许手工改。</p>
 */
@Service
public class ArtifactService {

    private final ArtifactRepository artifactRepository;

    public ArtifactService(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    public List<Artifact> list(String status) {
        if (status != null && !status.isBlank()) {
            return artifactRepository.findByStatus(status);
        }
        return artifactRepository.findAll();
    }

    public Artifact get(Long id) {
        return artifactRepository.findById(id)
                .orElseThrow(() -> new BizException("藏品不存在：" + id));
    }

    @Transactional
    public Artifact create(Artifact input) {
        if (input.getArtifactNo() == null || input.getArtifactNo().isBlank()) {
            throw new BizException("藏品编号不能为空");
        }
        artifactRepository.findByArtifactNo(input.getArtifactNo().trim()).ifPresent(a -> {
            throw new BizException("藏品编号已存在：" + input.getArtifactNo());
        });
        if (input.getAcquireYear() == null) {
            throw new BizException("入藏年份不能为空");
        }
        int currentYear = LocalDateTime.now().getYear();
        if (input.getAcquireYear() > currentYear) {
            throw new BizException("入藏年份不能晚于当前年份（当前：" + currentYear + "）");
        }
        if (input.getStatus() == null || input.getStatus().isBlank()) {
            input.setStatus("库房");
        } else if (!Dict.isValidArtifactStatus(input.getStatus())) {
            throw new BizException("藏品状态非法：" + input.getStatus());
        } else if (!Dict.canFreelyTransferArtifact(input.getStatus())) {
            throw new BizException("「" + input.getStatus() + "」状态只能由出展交接流程驱动，登记藏品请选择 在展 / 库房 / 借展");
        }
        input.setCreatedAt(LocalDateTime.now());
        return artifactRepository.save(input);
    }

    @Transactional
    public Artifact update(Long id, Artifact input) {
        Artifact existing = get(id);
        if (input.getArtifactNo() != null && !input.getArtifactNo().equals(existing.getArtifactNo())) {
            artifactRepository.findByArtifactNo(input.getArtifactNo().trim()).ifPresent(a -> {
                throw new BizException("藏品编号已存在：" + input.getArtifactNo());
            });
            existing.setArtifactNo(input.getArtifactNo().trim());
        }
        if (input.getName() != null) {
            existing.setName(input.getName());
        }
        if (input.getDynasty() != null) {
            existing.setDynasty(input.getDynasty());
        }
        if (input.getAcquireYear() != null) {
            int currentYear = LocalDateTime.now().getYear();
            if (input.getAcquireYear() > currentYear) {
                throw new BizException("入藏年份不能晚于当前年份（当前：" + currentYear + "）");
            }
            existing.setAcquireYear(input.getAcquireYear());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            if (!Dict.isValidArtifactStatus(input.getStatus())) {
                throw new BizException("藏品状态非法：" + input.getStatus());
            }
            checkHandoverStatusEditable(existing.getStatus(), input.getStatus());
            existing.setStatus(input.getStatus());
        }
        if (input.getAttrs() != null) {
            existing.setAttrs(input.getAttrs());
        }
        return artifactRepository.save(existing);
    }

    @Transactional
    public Artifact transfer(Long id, String target) {
        Artifact existing = get(id);
        if (!Dict.isValidArtifactStatus(target)) {
            throw new BizException("藏品状态非法：" + target);
        }
        checkHandoverStatusEditable(existing.getStatus(), target);
        existing.setStatus(target);
        return artifactRepository.save(existing);
    }

    /**
     * 「出展中」只能由出展交接流程驱动（确认交出进入、拒收 / 返库收口退出），
     * 不允许手工改入或改出，否则会跳过交接记录，导致藏品端与交接单两端状态不一致。
     */
    private void checkHandoverStatusEditable(String current, String target) {
        if (target.equals(current)) {
            return;
        }
        if (!Dict.canFreelyTransferArtifact(target)) {
            throw new BizException("「" + target + "」状态只能由出展交接流程驱动，请通过交接单确认交出");
        }
        if (!Dict.canFreelyTransferArtifact(current)) {
            throw new BizException("藏品当前为「" + current + "」，须先完成交接单的拒收或返库收口，不能直接改状态");
        }
    }
}
