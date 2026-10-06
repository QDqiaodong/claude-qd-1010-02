package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Gallery;
import com.museum.collection.repository.GalleryRepository;

/**
 * 展厅业务。
 *
 * <p>核心规则：① 容量必须为正整数；② 状态须在 开放 / 维护 / 关闭 之内。</p>
 */
@Service
public class GalleryService {

    private final GalleryRepository galleryRepository;

    public GalleryService(GalleryRepository galleryRepository) {
        this.galleryRepository = galleryRepository;
    }

    public List<Gallery> list(String status) {
        if (status != null && !status.isBlank()) {
            return galleryRepository.findByStatus(status);
        }
        return galleryRepository.findAll();
    }

    public Gallery get(Long id) {
        return galleryRepository.findById(id)
                .orElseThrow(() -> new BizException("展厅不存在：" + id));
    }

    @Transactional
    public Gallery create(Gallery input) {
        if (input.getGalleryCode() == null || input.getGalleryCode().isBlank()) {
            throw new BizException("展厅编号不能为空");
        }
        galleryRepository.findByGalleryCode(input.getGalleryCode().trim()).ifPresent(g -> {
            throw new BizException("展厅编号已存在：" + input.getGalleryCode());
        });
        if (input.getCapacity() == null || input.getCapacity() <= 0) {
            throw new BizException("展厅容量必须为正整数");
        }
        if (input.getStatus() == null || input.getStatus().isBlank()) {
            input.setStatus("开放");
        } else if (!Dict.isValidGalleryStatus(input.getStatus())) {
            throw new BizException("展厅状态非法：" + input.getStatus());
        }
        input.setCreatedAt(LocalDateTime.now());
        return galleryRepository.save(input);
    }

    @Transactional
    public Gallery update(Long id, Gallery input) {
        Gallery existing = get(id);
        if (input.getGalleryCode() != null && !input.getGalleryCode().equals(existing.getGalleryCode())) {
            galleryRepository.findByGalleryCode(input.getGalleryCode().trim()).ifPresent(g -> {
                throw new BizException("展厅编号已存在：" + input.getGalleryCode());
            });
            existing.setGalleryCode(input.getGalleryCode().trim());
        }
        if (input.getName() != null) {
            existing.setName(input.getName());
        }
        if (input.getCapacity() != null) {
            if (input.getCapacity() <= 0) {
                throw new BizException("展厅容量必须为正整数");
            }
            existing.setCapacity(input.getCapacity());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            if (!Dict.isValidGalleryStatus(input.getStatus())) {
                throw new BizException("展厅状态非法：" + input.getStatus());
            }
            existing.setStatus(input.getStatus());
        }
        return galleryRepository.save(existing);
    }

    @Transactional
    public Gallery transfer(Long id, String target) {
        Gallery existing = get(id);
        if (!Dict.isValidGalleryStatus(target)) {
            throw new BizException("展厅状态非法：" + target);
        }
        existing.setStatus(target);
        return galleryRepository.save(existing);
    }
}
