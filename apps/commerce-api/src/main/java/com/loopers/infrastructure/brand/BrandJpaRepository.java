package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.vo.BrandId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
    boolean existsByBrandId(BrandId brandId);
    Optional<BrandModel> findByBrandId(BrandId brandId);
}
