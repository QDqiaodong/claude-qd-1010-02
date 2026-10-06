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
import com.museum.collection.entity.PointAdjustment;
import com.museum.collection.service.PointAdjustmentService;

/** 会员积分调整申请与复核接口 */
@RestController
@RequestMapping("/api/point-adjustments")
public class PointAdjustmentController {

    private final PointAdjustmentService adjustmentService;

    public PointAdjustmentController(PointAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    /** 申请列表：支持按状态、会员组合筛选（会员详情的历史也走这里） */
    @GetMapping
    public ApiResponse<List<PointAdjustment>> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long memberId) {
        return ApiResponse.success(adjustmentService.list(status, memberId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PointAdjustment> get(@PathVariable Long id) {
        return ApiResponse.success(adjustmentService.get(id));
    }

    /** 提交申请：只落待复核记录，不动会员积分 */
    @PostMapping
    public ApiResponse<PointAdjustment> create(@RequestBody PointAdjustment input) {
        return ApiResponse.success("申请已提交，待复核", adjustmentService.create(input));
    }

    /** 修正待复核申请（调整值 / 原因 / 凭据），修正后仍为待复核 */
    @PutMapping("/{id}")
    public ApiResponse<PointAdjustment> update(@PathVariable Long id, @RequestBody PointAdjustment input) {
        return ApiResponse.success("申请已修正，待复核", adjustmentService.update(id, input));
    }

    /** 申请人撤回（仅待复核，且须本人） */
    @PostMapping("/{id}/withdraw")
    public ApiResponse<PointAdjustment> withdraw(@PathVariable Long id,
                                                 @RequestParam(required = false) String operator) {
        return ApiResponse.success("申请已撤回", adjustmentService.withdraw(id, operator));
    }

    /** 复核通过：同事务更新会员积分并重算等级；扣减为负时拒绝且申请保持待复核 */
    @PostMapping("/{id}/approve")
    public ApiResponse<PointAdjustment> approve(@PathVariable Long id,
                                                @RequestParam(required = false) String reviewer,
                                                @RequestParam(required = false) String comment) {
        return ApiResponse.success("复核通过，积分已更新", adjustmentService.approve(id, reviewer, comment));
    }

    /** 复核驳回：必须填复核意见，不动会员积分 */
    @PostMapping("/{id}/reject")
    public ApiResponse<PointAdjustment> reject(@PathVariable Long id,
                                               @RequestParam(required = false) String reviewer,
                                               @RequestParam(required = false) String comment) {
        return ApiResponse.success("已驳回", adjustmentService.reject(id, reviewer, comment));
    }
}
