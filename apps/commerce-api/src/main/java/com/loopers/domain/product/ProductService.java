package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public ProductModel createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        // 중복 체크
        if (productRepository.existsByProductId(new ProductId(productId))) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 상품 ID입니다.");
        }

        // 브랜드 존재 확인 및 PK 획득
        BrandModel brand = brandRepository.findByBrandId(new BrandId(brandId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
        Long refBrandId = brand.getId();

        // 상품 생성
        ProductModel product = ProductModel.create(productId, refBrandId, productName, price, stockQuantity);

        // 저장
        return productRepository.save(product);
    }

    public ProductModel getProduct(String productId) {
        return productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));
    }

    public ProductModel updateProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        product.update(productName, price, stockQuantity);
        return productRepository.save(product);
    }

    public void deleteProduct(String productId) {
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        // Soft delete
        product.markAsDeleted();
        productRepository.save(product);
    }

    public ProductModel getProductByRefId(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 상품이 존재하지 않습니다."));
    }

    public Page<ProductModel> getProducts(String brandId, String sortBy, Pageable pageable) {
        // brandId가 제공되면 Brand PK로 변환
        Long refBrandId = null;
        if (brandId != null && !brandId.isBlank()) {
            BrandModel brand = brandRepository.findByBrandId(new BrandId(brandId))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 브랜드가 존재하지 않습니다."));
            refBrandId = brand.getId();
        }
        return productRepository.findProducts(refBrandId, sortBy, pageable);
    }

    public long countLikes(Long productId) {
        return productRepository.countLikes(productId);
    }

    public void deleteProductsByBrandRefId(Long brandDbId) {
        List<ProductModel> products = productRepository.findByRefBrandId(brandDbId);
        for (ProductModel product : products) {
            product.markAsDeleted();
            productRepository.save(product);
        }
    }
}
