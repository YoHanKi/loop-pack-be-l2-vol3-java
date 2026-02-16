package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

public record BrandInfo(
        Long id,
        String brandId,
        String brandName
) {

    public static BrandInfo from(BrandModel brand) {
        return new BrandInfo(
                brand.getId(),
                brand.getBrandId().value(),
                brand.getBrandName().value()
        );
    }
}
