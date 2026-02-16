package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;

import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> findByBrandId(BrandId brandId);
    Optional<BrandModel> findById(Long id);
    boolean existsByBrandId(BrandId brandId);
}
