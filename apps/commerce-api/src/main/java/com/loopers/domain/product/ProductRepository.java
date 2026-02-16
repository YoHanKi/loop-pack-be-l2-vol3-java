package com.loopers.domain.product;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.product.vo.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> findByProductId(ProductId productId);

    boolean existsByProductId(ProductId productId);

    Page<ProductModel> findProducts(BrandId brandId, String sortBy, Pageable pageable);
}
