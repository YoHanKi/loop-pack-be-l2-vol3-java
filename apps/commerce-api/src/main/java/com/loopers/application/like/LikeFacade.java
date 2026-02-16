package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;

    public LikeInfo addLike(Long memberId, String productId) {
        var like = likeService.addLike(memberId, productId);
        return LikeInfo.from(like);
    }

    public void removeLike(Long memberId, String productId) {
        likeService.removeLike(memberId, productId);
    }
}
