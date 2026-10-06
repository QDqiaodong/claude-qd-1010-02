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
import com.museum.collection.entity.Handover;
import com.museum.collection.service.HandoverService;

/** 藏品出展交接接口 */
@RestController
@RequestMapping("/api/handovers")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @GetMapping
    public ApiResponse<List<Handover>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long artifactId,
                                            @RequestParam(required = false) Long exhibitionId) {
        return ApiResponse.success(handoverService.list(status, artifactId, exhibitionId));
    }

    /** 藏品当前占用中的交接单（待接收 / 已接收），用于从藏品端定位当前交接 */
    @GetMapping("/current")
    public ApiResponse<Handover> current(@RequestParam Long artifactId) {
        return ApiResponse.success(handoverService.currentForArtifact(artifactId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Handover> get(@PathVariable Long id) {
        return ApiResponse.success(handoverService.get(id));
    }

    @PostMapping
    public ApiResponse<Handover> create(@RequestBody Handover input) {
        return ApiResponse.success("交接单已创建（草稿）", handoverService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Handover> update(@PathVariable Long id, @RequestBody Handover input) {
        return ApiResponse.success("草稿已保存", handoverService.update(id, input));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Handover> confirm(@PathVariable Long id) {
        return ApiResponse.success("已确认交出，藏品进入出展中", handoverService.confirm(id));
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<Handover> receive(@PathVariable Long id) {
        return ApiResponse.success("已确认接收", handoverService.receive(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Handover> reject(@PathVariable Long id,
                                        @RequestParam(required = false) String reason) {
        return ApiResponse.success("已拒收，藏品归还库房管理", handoverService.reject(id, reason));
    }

    @PostMapping("/{id}/return")
    public ApiResponse<Handover> returnBack(@PathVariable Long id,
                                            @RequestParam(required = false) String handler,
                                            @RequestParam(required = false) String condition) {
        return ApiResponse.success("返库完成，交接单已收口", handoverService.returnBack(id, handler, condition));
    }
}
