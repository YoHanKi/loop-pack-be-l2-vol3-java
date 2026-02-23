package com.loopers.application.like;

import com.loopers.application.brand.BrandApp;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductApp;
import com.loopers.application.product.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeApp likeApp;
    private final ProductApp productApp;
    private final BrandApp brandApp;

    public Page<LikedProductInfo> getMyLikedProducts(Long memberId, Pageable pageable) {
        return likeApp.getMyLikes(memberId, pageable)
                .map(like -> {
                    ProductInfo product = productApp.getProductByRefId(like.refProductId());
                    BrandInfo brand = brandApp.getBrandByRefId(product.refBrandId());
                    return new LikedProductInfo(
                            product.productId(),
                            product.productName(),
                            brand.brandName(),
                            product.price(),
                            like.likedAt()
                    );
                });
    }
}
