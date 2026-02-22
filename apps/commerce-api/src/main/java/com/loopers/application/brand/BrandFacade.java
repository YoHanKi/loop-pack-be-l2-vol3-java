package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @Transactional
    public BrandInfo createBrand(String brandId, String brandName) {
        BrandModel brand = brandService.createBrand(brandId, brandName);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(String brandId) {
        BrandModel brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(String brandId) {
        BrandModel brand = brandService.deleteBrand(brandId);
        productService.deleteProductsByBrandRefId(brand.getId());
    }
}
