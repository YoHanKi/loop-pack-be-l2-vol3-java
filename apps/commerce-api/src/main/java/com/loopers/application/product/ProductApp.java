package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
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

    @Transactional
    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.createProduct(productId, brandId, productName, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(String productId) {
        ProductModel product = productService.getProduct(productId);
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
        ProductModel product = productService.getProductByRefId(id);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public long countLikes(Long productId) {
        return productService.countLikes(productId);
    }

    @Transactional
    public void deleteProductsByBrandRefId(Long brandId) {
        productService.deleteProductsByBrandRefId(brandId);
    }
}
