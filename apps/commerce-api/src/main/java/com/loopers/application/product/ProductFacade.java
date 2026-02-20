package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.createProduct(productId, brandId, productName, price, stockQuantity);
        return enrichProductInfo(product);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(String productId) {
        ProductModel product = productService.getProduct(productId);
        return enrichProductInfo(product);
    }

    @Transactional
    public ProductInfo updateProduct(String productId, String productName, java.math.BigDecimal price, int stockQuantity) {
        ProductModel product = productService.updateProduct(productId, productName, price, stockQuantity);
        return enrichProductInfo(product);
    }

    @Transactional
    public void deleteProduct(String productId) {
        productService.deleteProduct(productId);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(String brandId, String sortBy, Pageable pageable) {
        Page<ProductModel> products = productService.getProducts(brandId, sortBy, pageable);
        return products.map(this::enrichProductInfo);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProductByDbId(Long id) {
        ProductModel product = productService.getProductByDbId(id);
        return enrichProductInfo(product);
    }

    /**
     * ProductModel에 Brand 정보와 좋아요 수를 추가하여 ProductInfo 생성
     */
    private ProductInfo enrichProductInfo(ProductModel product) {
        // Brand 정보 조회
        BrandModel brand = brandRepository.findById(product.getRefBrandId().value())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "브랜드 정보를 찾을 수 없습니다. ID: " + product.getRefBrandId().value()));

        // 좋아요 수 조회
        long likesCount = productRepository.countLikes(product.getId());

        return ProductInfo.from(product, brand, likesCount);
    }
}
