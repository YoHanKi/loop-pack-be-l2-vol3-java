package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

public class BrandV1Dto {

    public record CreateBrandRequest(
            @NotBlank String brandId,
            @NotBlank String brandName
    ) {}

    public record BrandResponse(
            Long id,
            String brandId,
            String brandName
    ) {
        public static BrandResponse fromInfo(BrandInfo info) {
            return new BrandResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName()
            );
        }
    }
}
