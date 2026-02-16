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
public class ProductFacade {

    private final ProductService productService;

    @Transactional
    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.createProduct(productId, brandId, productName, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional
    public void deleteProduct(String productId) {
        productService.deleteProduct(productId);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(String brandId, String sortBy, Pageable pageable) {
        Page<ProductModel> products = productService.getProducts(brandId, sortBy, pageable);
        return products.map(ProductInfo::from);
    }
}
