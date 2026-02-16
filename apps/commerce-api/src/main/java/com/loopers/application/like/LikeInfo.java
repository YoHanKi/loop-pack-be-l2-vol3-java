package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;

public record LikeInfo(
        Long id,
        Long refMemberId,
        Long refProductId
) {
    public static LikeInfo from(LikeModel like) {
        return new LikeInfo(
                like.getId(),
                like.getRefMemberId().value(),
                like.getRefProductId().value()
        );
    }
}
