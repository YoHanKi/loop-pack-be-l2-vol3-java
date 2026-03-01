package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.vo.BrandId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandApp {

    private final BrandService brandService;
    private final BrandRepository brandRepository;

    @Transactional
    public BrandInfo createBrand(String brandId, String brandName) {
        BrandModel brand = brandService.createBrand(brandId, brandName);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(String brandId) {
        BrandModel brand = brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo deleteBrand(String brandId) {
        BrandModel brand = brandService.deleteBrand(brandId);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrandByRefId(Long id) {
        BrandModel brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
        return BrandInfo.from(brand);
    }
}
