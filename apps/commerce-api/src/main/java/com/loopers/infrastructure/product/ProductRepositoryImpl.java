package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> findByProductId(ProductId productId) {
        return productJpaRepository.findByProductId(productId);
    }

    @Override
    public boolean existsByProductId(ProductId productId) {
        return productJpaRepository.existsByProductId(productId);
    }

    @Override
    public Page<ProductModel> findProducts(Long refBrandId, String sortBy, Pageable pageable) {
        if ("likes_desc".equals(sortBy)) {
            return productJpaRepository.findActiveSortByLikesDesc(refBrandId, pageable);
        } else if ("price_asc".equals(sortBy)) {
            return productJpaRepository.findActiveSortByPriceAsc(refBrandId, pageable);
        }
        return productJpaRepository.findActiveSortByLatest(refBrandId, pageable);
    }

    @Override
    public boolean decreaseStockIfAvailable(Long productId, int quantity) {
        return productJpaRepository.decreaseStockIfAvailable(productId, quantity) > 0;
    }

    @Override
    public void increaseStock(Long productId, int quantity) {
        productJpaRepository.increaseStock(productId, quantity);
    }

    @Override
    public long countLikes(Long productId) {
        return productJpaRepository.countLikesByProductId(productId);
    }

    @Override
    public List<ProductModel> findByRefBrandId(Long brandId) {
        return productJpaRepository.findActiveByRefBrandId(brandId);
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findById(id);
    }
}
