package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberApp;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberApp memberApp;

    @PostMapping("/register")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> register(@Valid @RequestBody MemberV1Dto.RegisterRequest request) {
        MemberInfo info = memberApp.register(
            request.memberId(),
            request.password(),
            request.email(),
            request.birthDate(),
            request.name(),
            request.gender()
        );

        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.fromInfo(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.MeResponse> getMe(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        MemberInfo info = memberApp.authenticate(loginId, loginPw);

        MemberV1Dto.MeResponse response = MemberV1Dto.MeResponse.fromInfo(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Void> changePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @Valid @RequestBody MemberV1Dto.ChangePasswordRequest request
    ) {
        memberApp.changePassword(loginId, loginPw,
                request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
