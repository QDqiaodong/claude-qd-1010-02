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
import com.museum.collection.entity.Member;
import com.museum.collection.service.MemberService;

/** 会员接口 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<Member>> list(@RequestParam(required = false) String level) {
        return ApiResponse.success(memberService.list(level));
    }

    @GetMapping("/{id}")
    public ApiResponse<Member> get(@PathVariable Long id) {
        return ApiResponse.success(memberService.get(id));
    }

    @PostMapping
    public ApiResponse<Member> create(@RequestBody Member input) {
        return ApiResponse.success("会员已录入", memberService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Member> update(@PathVariable Long id, @RequestBody Member input) {
        return ApiResponse.success("已更新", memberService.update(id, input));
    }

    @PostMapping("/{id}/points")
    public ApiResponse<Member> addPoints(@PathVariable Long id, @RequestParam int delta) {
        return ApiResponse.success("积分已更新", memberService.addPoints(id, delta));
    }
}
