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
 * <p>核心规则：① 新增导览受展厅容量约束——仅「待开始 / 进行中」的导览占用容量，
 * 已完成导览仍保留在历史中，但不再阻塞同一展厅继续排期；
 * ② 状态只能逐级推进：待开始 → 进行中 → 已完成。</p>
 *
 * <p>并发口径：排期和状态流转都先锁展厅行，再检查/锁场次行。同一展厅的容量增减
 * 因此串行执行：并发结束只有一场会成功，并发排期不会超过容量，失败事务整体回滚。</p>
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
        List<Tour> base;
        if (galleryId != null) {
            base = tourRepository.findByGalleryId(galleryId);
        } else if (status != null && !status.isBlank()) {
            base = tourRepository.findByStatus(status.trim());
        } else {
            base = tourRepository.findAll();
        }
        if (galleryId != null && status != null && !status.isBlank()) {
            base = base.stream().filter(t -> status.trim().equals(t.getStatus())).toList();
        }
        return base;
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
        String galleryCode = input.getGalleryCode().trim();
        Gallery gallery = galleryRepository.findByGalleryCodeForUpdate(galleryCode)
                .orElseThrow(() -> new BizException("展厅不存在：" + galleryCode));

        if (input.getTourNo() != null && !input.getTourNo().isBlank()) {
            tourRepository.findByTourNo(input.getTourNo().trim()).ifPresent(t -> {
                throw new BizException("导览编号已存在：" + input.getTourNo());
            });
        }
        String status = input.getStatus() != null && !input.getStatus().isBlank()
                ? input.getStatus().trim() : "待开始";
        if (!Dict.isValidTourStatus(status)) {
            throw new BizException("导览状态非法：" + status);
        }

        long used = tourRepository
                .findOccupyingByGalleryIdForUpdate(gallery.getId(), Dict.TOUR_OCCUPYING_STATUSES)
                .size();
        if (used >= gallery.getCapacity()) {
            throw new BizException("展厅「" + gallery.getName() + "」容量为 " + gallery.getCapacity()
                    + "，当前待开始/进行中已有 " + used + " 场导览，容量已满，无法新增导览");
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
        tour.setStatus(status);
        tour.setCreatedAt(LocalDateTime.now());
        return tourRepository.save(tour);
    }

    @Transactional
    public Tour start(Long id) {
        return transfer(id, "进行中");
    }

    @Transactional
    public Tour finish(Long id) {
        return transfer(id, "已完成");
    }

    @Transactional
    public Tour transfer(Long id, String target) {
        if (!Dict.isValidTourStatus(target)) {
            throw new BizException("导览状态非法：" + target);
        }
        Long galleryId = tourRepository.findGalleryIdById(id)
                .orElseThrow(() -> new BizException("导览不存在：" + id));
        // 先锁展厅，再锁导览；与排期使用相同锁顺序，保证容量释放和新增不会互相读到中间状态
        lockGallery(galleryId);
        Tour existing = tourRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("导览不存在：" + id));
        if (!Dict.canTransferTour(existing.getStatus(), target)) {
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」跳转到「" + target + "」");
        }
        existing.setStatus(target);
        return tourRepository.save(existing);
    }

    private Gallery lockGallery(Long galleryId) {
        if (galleryId == null) {
            throw new BizException("导览未关联展厅，不能流转");
        }
        return galleryRepository.findByIdForUpdate(galleryId)
                .orElseThrow(() -> new BizException("展厅不存在：" + galleryId));
    }
}
