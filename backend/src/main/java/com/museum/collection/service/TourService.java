package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Gallery;
import com.museum.collection.entity.Tour;
import com.museum.collection.repository.GalleryRepository;
import com.museum.collection.repository.TourRepository;

/**
 * 导览业务。
 *
 * <p>核心规则：① 新增导览受展厅容量约束——该展厅已有导览数不得超过其 capacity，
 * 超出则拒绝（中文 BizException）；② 状态只能逐级推进：待开始 → 进行中 → 已完成。</p>
 */
@Service
public class TourService {

    private final TourRepository tourRepository;
    private final GalleryRepository galleryRepository;

    public TourService(TourRepository tourRepository, GalleryRepository galleryRepository) {
        this.tourRepository = tourRepository;
        this.galleryRepository = galleryRepository;
    }

    public List<Tour> list(Long galleryId, String status) {
        if (galleryId != null) {
            return tourRepository.findByGalleryId(galleryId);
        }
        if (status != null && !status.isBlank()) {
            return tourRepository.findByStatus(status);
        }
        return tourRepository.findAll();
    }

    public Tour get(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new BizException("导览不存在：" + id));
    }

    private String nextTourNo() {
        int year = LocalDateTime.now().getYear();
        int maxSeq = 0;
        Pattern p = Pattern.compile("^TO-" + year + "-(\\d+)$");
        for (Tour t : tourRepository.findAll()) {
            if (t.getTourNo() == null) {
                continue;
            }
            Matcher m = p.matcher(t.getTourNo());
            if (m.find()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(m.group(1)));
            }
        }
        return String.format("TO-%d-%03d", year, maxSeq + 1);
    }

    @Transactional
    public Tour create(Tour input) {
        if (input.getGalleryCode() == null || input.getGalleryCode().isBlank()) {
            throw new BizException("必须指定所属展厅编号");
        }
        Gallery gallery = galleryRepository.findByGalleryCode(input.getGalleryCode().trim())
                .orElseThrow(() -> new BizException("展厅不存在：" + input.getGalleryCode()));
        long used = tourRepository.countByGalleryId(gallery.getId());
        if (used >= gallery.getCapacity()) {
            throw new BizException("展厅「" + gallery.getName() + "」容量为 " + gallery.getCapacity()
                    + "，当前已有 " + used + " 场导览，超出容量，无法新增导览");
        }
        if (input.getTourNo() != null && !input.getTourNo().isBlank()) {
            tourRepository.findByTourNo(input.getTourNo().trim()).ifPresent(t -> {
                throw new BizException("导览编号已存在：" + input.getTourNo());
            });
        }
        Tour tour = new Tour();
        tour.setTourNo(input.getTourNo() != null && !input.getTourNo().isBlank()
                ? input.getTourNo().trim() : nextTourNo());
        tour.setGalleryId(gallery.getId());
        tour.setGalleryCode(gallery.getGalleryCode());
        tour.setGalleryName(gallery.getName());
        tour.setTitle(input.getTitle());
        tour.setGuideName(input.getGuideName());
        tour.setScheduledAt(input.getScheduledAt());
        tour.setStatus(input.getStatus() != null && !input.getStatus().isBlank() ? input.getStatus() : "待开始");
        if (!Dict.isValidTourStatus(tour.getStatus())) {
            throw new BizException("导览状态非法：" + tour.getStatus());
        }
        tour.setCreatedAt(LocalDateTime.now());
        return tourRepository.save(tour);
    }

    @Transactional
    public Tour start(Long id) {
        Tour existing = get(id);
        if (!Dict.canTransferTour(existing.getStatus(), "进行中")) {
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」开始");
        }
        existing.setStatus("进行中");
        return tourRepository.save(existing);
    }

    @Transactional
    public Tour finish(Long id) {
        Tour existing = get(id);
        if (!Dict.canTransferTour(existing.getStatus(), "已完成")) {
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」结束");
        }
        existing.setStatus("已完成");
        return tourRepository.save(existing);
    }

    @Transactional
    public Tour transfer(Long id, String target) {
        Tour existing = get(id);
        if (!Dict.isValidTourStatus(target)) {
            throw new BizException("导览状态非法：" + target);
        }
        if (!Dict.canTransferTour(existing.getStatus(), target)) {
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」跳转到「" + target + "」");
        }
        existing.setStatus(target);
        return tourRepository.save(existing);
    }
}
