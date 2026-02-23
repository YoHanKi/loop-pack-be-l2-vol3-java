package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.vo.ProductId;
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
public class ProductApp {

    private final ProductService productService;
    private final ProductRepository productRepository;

    @Transactional
    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.createProduct(productId, brandId, productName, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(String productId) {
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));
        return ProductInfo.from(product);
    }

    @Transactional
    public ProductInfo updateProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.updateProduct(productId, productName, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional
    public void deleteProduct(String productId) {
        productService.deleteProduct(productId);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(String brandId, String sortBy, Pageable pageable) {
        return productService.getProducts(brandId, sortBy, pageable).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProductByRefId(Long id) {
        ProductModel product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 상품이 존재하지 않습니다."));
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public long countLikes(Long productId) {
        return productRepository.countLikes(productId);
    }

    @Transactional
    public void deleteProductsByBrandRefId(Long brandId) {
        productService.deleteProductsByBrandRefId(brandId);
    }
}
