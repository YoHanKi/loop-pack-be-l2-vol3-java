package com.loopers.application.like;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductFacade productFacade;

    public LikeInfo addLike(Long memberId, String productId) {
        LikeModel like = likeService.addLike(memberId, productId);
        return LikeInfo.from(like);
    }

    public void removeLike(Long memberId, String productId) {
        likeService.removeLike(memberId, productId);
    }

    @Transactional(readOnly = true)
    public Page<LikedProductInfo> getMyLikedProducts(Long memberId, Pageable pageable) {
        return likeService.getMyLikes(memberId, pageable)
                .map(like -> {
                    ProductInfo product = productFacade.getProductByDbId(like.getRefProductId().value());
                    return new LikedProductInfo(
                            product.productId(),
                            product.productName(),
                            product.brand().brandName(),
                            product.price(),
                            like.getCreatedAt()
                    );
                });
    }
}
