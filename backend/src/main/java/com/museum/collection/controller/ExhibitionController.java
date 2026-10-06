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
import com.museum.collection.entity.Exhibition;
import com.museum.collection.service.ExhibitionService;

/** 展览接口 */
@RestController
@RequestMapping("/api/exhibitions")
public class ExhibitionController {

    private final ExhibitionService exhibitionService;

    public ExhibitionController(ExhibitionService exhibitionService) {
        this.exhibitionService = exhibitionService;
    }

    @GetMapping
    public ApiResponse<List<Exhibition>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(exhibitionService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Exhibition> get(@PathVariable Long id) {
        return ApiResponse.success(exhibitionService.get(id));
    }

    @PostMapping
    public ApiResponse<Exhibition> create(@RequestBody Exhibition input) {
        return ApiResponse.success("展览已创建", exhibitionService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Exhibition> update(@PathVariable Long id, @RequestBody Exhibition input) {
        return ApiResponse.success("已更新", exhibitionService.update(id, input));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Exhibition> transfer(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("状态已更新", exhibitionService.transfer(id, target));
    }
}
