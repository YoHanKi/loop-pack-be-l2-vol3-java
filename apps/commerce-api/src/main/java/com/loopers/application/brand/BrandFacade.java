package com.loopers.application.brand;

import com.loopers.application.product.ProductApp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandApp brandApp;
    private final ProductApp productApp;

    @Transactional
    public void deleteBrand(String brandId) {
        BrandInfo brand = brandApp.deleteBrand(brandId);
        productApp.deleteProductsByBrandRefId(brand.id());
    }
}
