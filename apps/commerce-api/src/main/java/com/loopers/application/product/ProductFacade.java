package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
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
    private final BrandService brandService;

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
    public ProductInfo updateProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
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
    public ProductInfo getProductByRefId(Long id) {
        ProductModel product = productService.getProductByRefId(id);
        return enrichProductInfo(product);
    }

    private ProductInfo enrichProductInfo(ProductModel product) {
        BrandModel brand = brandService.getBrandByRefId(product.getRefBrandId().value());
        long likesCount = productService.countLikes(product.getId());
        return ProductInfo.from(product, brand, likesCount);
    }
}
