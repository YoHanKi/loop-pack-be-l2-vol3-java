package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;

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
    public Page<LikedProductInfo> getMyLikedProducts(Long memberId, Pageable pageable) {
        return likeService.getMyLikes(memberId, pageable)
                .map(like -> {
                    ProductModel product = productService.getProductByRefId(like.getRefProductId().value());
                    BrandModel brand = brandService.getBrandByRefId(product.getRefBrandId().value());
                    return new LikedProductInfo(
                            product.getProductId().value(),
                            product.getProductName().value(),
                            brand.getBrandName().value(),
                            product.getPrice().value(),
                            like.getCreatedAt()
                    );
                });
    }
}
