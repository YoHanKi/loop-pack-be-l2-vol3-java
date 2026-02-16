package com.loopers.domain.product;

import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductReader {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductModel getOrThrow(String productId) {
        return productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean exists(String productId) {
        return productRepository.existsByProductId(new ProductId(productId));
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> findProducts(Long refBrandId, String sortBy, Pageable pageable) {
        return productRepository.findProducts(refBrandId, sortBy, pageable);
    }
}
