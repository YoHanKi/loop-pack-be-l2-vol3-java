package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LikeV1Dto {

    public record AddLikeRequest(
            @NotNull(message = "회원 ID는 필수입니다.")
            Long memberId
    ) {}

    public record RemoveLikeRequest(
            @NotNull(message = "회원 ID는 필수입니다.")
            Long memberId
    ) {}

    public record LikeResponse(
            Long id,
            Long refMemberId,
            Long refProductId
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                    info.id(),
                    info.refMemberId(),
                    info.refProductId()
            );
        }
    }
}
