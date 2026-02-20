package com.loopers.application.like;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record LikedProductInfo(
        String productId,
        String productName,
        String brandName,
        BigDecimal price,
        ZonedDateTime likedAt
) {}
