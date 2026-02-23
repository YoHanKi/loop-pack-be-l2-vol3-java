package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;

import java.time.ZonedDateTime;

public record LikeInfo(
        Long id,
        Long refMemberId,
        Long refProductId,
        ZonedDateTime likedAt
) {
    public static LikeInfo from(LikeModel like) {
        return new LikeInfo(
                like.getId(),
                like.getRefMemberId().value(),
                like.getRefProductId().value(),
                like.getCreatedAt()
        );
    }
}
