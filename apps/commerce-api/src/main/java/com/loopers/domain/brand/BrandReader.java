package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandReader {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public BrandModel getOrThrow(String brandId) {
        return brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean exists(String brandId) {
        return brandRepository.existsByBrandId(new BrandId(brandId));
    }
}
