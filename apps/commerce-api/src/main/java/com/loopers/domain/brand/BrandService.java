package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandModel createBrand(String brandId, String brandName) {
        if (brandRepository.existsByBrandId(new BrandId(brandId))) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 ID입니다.");
        }

        BrandModel brand = BrandModel.create(brandId, brandName);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(String brandId) {
        return brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
    }

    @Transactional
    public void deleteBrand(String brandId) {
        BrandModel brand = brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));

        brand.markAsDeleted();
        brandRepository.save(brand);

        List<ProductModel> products = productRepository.findByRefBrandId(brand.getId());
        for (ProductModel product : products) {
            product.markAsDeleted();
            productRepository.save(product);
        }
    }
}
