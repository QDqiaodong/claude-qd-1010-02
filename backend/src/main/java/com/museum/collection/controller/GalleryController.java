package com.museum.collection.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.museum.collection.common.ApiResponse;
import com.museum.collection.entity.Gallery;
import com.museum.collection.service.GalleryService;

/** 展厅接口 */
@RestController
@RequestMapping("/api/galleries")
public class GalleryController {

    private final GalleryService galleryService;

    public GalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @GetMapping
    public ApiResponse<List<Gallery>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(galleryService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Gallery> get(@PathVariable Long id) {
        return ApiResponse.success(galleryService.get(id));
    }

    @PostMapping
    public ApiResponse<Gallery> create(@RequestBody Gallery input) {
        return ApiResponse.success("展厅已创建", galleryService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Gallery> update(@PathVariable Long id, @RequestBody Gallery input) {
        return ApiResponse.success("已更新", galleryService.update(id, input));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Gallery> transfer(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("状态已更新", galleryService.transfer(id, target));
    }
}
