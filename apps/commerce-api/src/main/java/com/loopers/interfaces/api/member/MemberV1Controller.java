package com.loopers.interfaces.api.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberService memberService;

    @PostMapping("/register")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> register(@Valid @RequestBody MemberV1Dto.RegisterRequest request) {
        MemberModel member = memberService.register(
            request.memberId(),
            request.password(),
            request.email(),
            request.birthDate(),
            request.name(),
            request.gender()
        );

        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(member);
        return ApiResponse.success(response);
    }
}
