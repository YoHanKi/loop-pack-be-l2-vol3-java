package com.loopers.application.product;

import com.loopers.application.brand.BrandApp;
import com.loopers.application.brand.BrandInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductApp productApp;
    private final BrandApp brandApp;

    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductInfo product = productApp.createProduct(productId, brandId, productName, price, stockQuantity);
        return enrichProductInfo(product);
    }

    public ProductInfo getProduct(String productId) {
        ProductInfo product = productApp.getProduct(productId);
        return enrichProductInfo(product);
    }

    public ProductInfo updateProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
        ProductInfo product = productApp.updateProduct(productId, productName, price, stockQuantity);
        return enrichProductInfo(product);
    }

    public void deleteProduct(String productId) {
        productApp.deleteProduct(productId);
    }

    public Page<ProductInfo> getProducts(String brandId, String sortBy, Pageable pageable) {
        Page<ProductInfo> products = productApp.getProducts(brandId, sortBy, pageable);
        return products.map(this::enrichProductInfo);
    }

    public ProductInfo getProductByRefId(Long id) {
        ProductInfo product = productApp.getProductByRefId(id);
        return enrichProductInfo(product);
    }

    private ProductInfo enrichProductInfo(ProductInfo product) {
        BrandInfo brand = brandApp.getBrandByRefId(product.refBrandId());
        long likesCount = productApp.countLikes(product.id());
        return product.enrich(brand, likesCount);
    }
}
