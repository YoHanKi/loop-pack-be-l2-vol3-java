package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandReader brandReader;

    @Transactional
    public BrandModel createBrand(String brandId, String brandName) {
        if (brandReader.exists(brandId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 ID입니다.");
        }

        BrandModel brand = BrandModel.create(brandId, brandName);
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(String brandId) {
        BrandModel brand = brandReader.getOrThrow(brandId);

        // TODO: Product 도메인 구현 후 상품 참조 체크 로직 추가
        // if (productReader.existsByBrandId(brandId)) {
        //     throw new CoreException(ErrorType.CONFLICT, "해당 브랜드를 참조하는 상품이 존재하여 삭제할 수 없습니다.");
        // }

        brand.markAsDeleted();
        brandRepository.save(brand);
    }
}
