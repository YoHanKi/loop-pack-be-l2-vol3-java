package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;
import com.loopers.application.like.LikedProductInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

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

    public record LikedProductResponse(
            String productId,
            String productName,
            String brandName,
            BigDecimal price,
            ZonedDateTime likedAt
    ) {
        public static LikedProductResponse from(LikedProductInfo info) {
            return new LikedProductResponse(
                    info.productId(),
                    info.productName(),
                    info.brandName(),
                    info.price(),
                    info.likedAt()
            );
        }
    }
}
