package com.museum.collection.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.museum.collection.common.ApiResponse;
import com.museum.collection.entity.Tour;
import com.museum.collection.service.TourService;

/** 导览接口 */
@RestController
@RequestMapping("/api/tours")
public class TourController {

    private final TourService tourService;

    public TourController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    public ApiResponse<List<Tour>> list(@RequestParam(required = false) Long galleryId,
                                        @RequestParam(required = false) String status) {
        return ApiResponse.success(tourService.list(galleryId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Tour> get(@PathVariable Long id) {
        return ApiResponse.success(tourService.get(id));
    }

    @PostMapping
    public ApiResponse<Tour> create(@RequestBody Tour input) {
        return ApiResponse.success("导览已排期", tourService.create(input));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<Tour> start(@PathVariable Long id) {
        return ApiResponse.success("导览开始", tourService.start(id));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<Tour> finish(@PathVariable Long id) {
        return ApiResponse.success("导览结束", tourService.finish(id));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Tour> transfer(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("状态已更新", tourService.transfer(id, target));
    }
}
