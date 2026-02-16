package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;

public record ProductInfo(
        Long id,
        String productId,
        String brandId,
        String productName,
        BigDecimal price,
        int stockQuantity
) {
    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
                product.getId(),
                product.getProductId().value(),
                product.getBrandId().value(),
                product.getProductName().value(),
                product.getPrice().value(),
                product.getStockQuantity().value()
        );
    }
}
