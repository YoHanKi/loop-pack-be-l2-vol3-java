package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandModel createBrand(String brandId, String brandName) {
        if (brandRepository.existsByBrandId(new BrandId(brandId))) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 ID입니다.");
        }

        BrandModel brand = BrandModel.create(brandId, brandName);
        return brandRepository.save(brand);
    }

    public BrandModel getBrand(String brandId) {
        return brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
    }

    public BrandModel getBrandByRefId(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
    }

    public BrandModel deleteBrand(String brandId) {
        BrandModel brand = brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));

        brand.markAsDeleted();
        return brandRepository.save(brand);
    }
}
