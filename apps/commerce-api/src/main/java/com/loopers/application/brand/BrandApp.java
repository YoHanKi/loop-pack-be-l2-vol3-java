package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandApp {

    private final BrandService brandService;

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
    public BrandInfo deleteBrand(String brandId) {
        BrandModel brand = brandService.deleteBrand(brandId);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrandByRefId(Long id) {
        BrandModel brand = brandService.getBrandByRefId(id);
        return BrandInfo.from(brand);
    }
}
