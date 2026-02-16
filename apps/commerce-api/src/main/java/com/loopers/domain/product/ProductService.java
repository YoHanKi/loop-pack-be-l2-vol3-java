package com.loopers.domain.product;

import com.loopers.domain.brand.BrandReader;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductReader productReader;
    private final BrandReader brandReader;

    @Transactional
    public ProductModel createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        // 중복 체크
        if (productReader.exists(productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 상품 ID입니다.");
        }

        // 브랜드 존재 확인
        brandReader.getOrThrow(brandId);

        // 상품 생성
        ProductModel product = ProductModel.create(productId, brandId, productName, price, stockQuantity);

        // 저장
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(String productId) {
        ProductModel product = productReader.getOrThrow(productId);

        // Soft delete
        product.markAsDeleted();
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getProducts(String brandId, String sortBy, Pageable pageable) {
        return productReader.findProducts(brandId, sortBy, pageable);
    }
}
