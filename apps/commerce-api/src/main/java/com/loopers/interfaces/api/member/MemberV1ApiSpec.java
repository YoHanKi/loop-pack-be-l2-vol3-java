package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "회원 관리 API", description = "회원 관련 API")
public interface MemberV1ApiSpec {
    @Operation(
            summary = "회원 등록",
            description = "새로운 회원을 등록합니다."
    )
    ApiResponse<MemberV1Dto.MemberResponse> register(
            @Schema(name = "회원 등록 요청 DTO", description = "회원 등록에 필요한 정보를 담고 있는 DTO")
            @Valid @RequestBody MemberV1Dto.RegisterRequest request
    );
}
