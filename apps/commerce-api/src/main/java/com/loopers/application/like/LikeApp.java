package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeApp {

    private final LikeService likeService;

    @Transactional
    public LikeInfo addLike(Long memberId, String productId) {
        LikeModel like = likeService.addLike(memberId, productId);
        return LikeInfo.from(like);
    }

    @Transactional
    public void removeLike(Long memberId, String productId) {
        likeService.removeLike(memberId, productId);
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> getMyLikes(Long memberId, Pageable pageable) {
        return likeService.getMyLikes(memberId, pageable).map(LikeInfo::from);
    }
}
