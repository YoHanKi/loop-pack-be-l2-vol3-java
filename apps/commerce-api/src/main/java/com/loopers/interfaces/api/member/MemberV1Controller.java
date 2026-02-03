package com.loopers.interfaces.api.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.MeResponse> getMe(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        MemberModel member = memberService.getMemberByMemberId(loginId);
        if (member == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "해당 ID의 회원이 존재하지 않습니다.");
        }

        memberService.validatePassword(loginPw, member.getPassword());

        MemberV1Dto.MeResponse response = MemberV1Dto.MeResponse.from(member);
        return ApiResponse.success(response);
    }
}
