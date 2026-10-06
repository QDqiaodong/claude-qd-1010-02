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
import com.museum.collection.entity.Artifact;
import com.museum.collection.service.ArtifactService;

/** 藏品接口 */
@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping
    public ApiResponse<List<Artifact>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(artifactService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Artifact> get(@PathVariable Long id) {
        return ApiResponse.success(artifactService.get(id));
    }

    @PostMapping
    public ApiResponse<Artifact> create(@RequestBody Artifact input) {
        return ApiResponse.success("藏品已登记", artifactService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Artifact> update(@PathVariable Long id, @RequestBody Artifact input) {
        return ApiResponse.success("已更新", artifactService.update(id, input));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Artifact> transfer(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("状态已更新", artifactService.transfer(id, target));
    }
}
