package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ProductCursor(
        String type,
        ZonedDateTime updatedAt,
        Integer likeCount,
        BigDecimal price,
        Long id
) {
    public static ProductCursor from(ProductModel last, String sortBy) {
        ProductCursorCondition condition = ProductCursorCondition.from(sortBy);
        return switch (condition) {
            case LATEST -> new ProductCursor("LATEST", last.getUpdatedAt(), null, null, last.getId());
            case LIKES_DESC -> new ProductCursor("LIKES_DESC", last.getUpdatedAt(), last.getLikeCount(), null, last.getId());
            case PRICE_ASC -> new ProductCursor("PRICE_ASC", null, null, last.getPrice().value(), last.getId());
        };
    }
}