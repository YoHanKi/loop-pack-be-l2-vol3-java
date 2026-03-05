package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.vo.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByProductId(ProductId productId);

    boolean existsByProductId(ProductId productId);

    @Query(
            value = "SELECT * FROM products WHERE deleted_at IS NULL AND (:refBrandId IS NULL OR ref_brand_id = :refBrandId) ORDER BY updated_at DESC",
            countQuery = "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND (:refBrandId IS NULL OR ref_brand_id = :refBrandId)",
            nativeQuery = true
    )
    Page<ProductModel> findActiveSortByLatest(@Param("refBrandId") Long refBrandId, Pageable pageable);

    @Query(
            value = "SELECT * FROM products WHERE deleted_at IS NULL AND (:refBrandId IS NULL OR ref_brand_id = :refBrandId) ORDER BY price ASC",
            countQuery = "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND (:refBrandId IS NULL OR ref_brand_id = :refBrandId)",
            nativeQuery = true
    )
    Page<ProductModel> findActiveSortByPriceAsc(@Param("refBrandId") Long refBrandId, Pageable pageable);

    @Query(
            value = "SELECT * FROM products WHERE deleted_at IS NULL AND (:refBrandId IS NULL OR ref_brand_id = :refBrandId) ORDER BY like_count DESC, updated_at DESC",
            countQuery = "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND (:refBrandId IS NULL OR ref_brand_id = :refBrandId)",
            nativeQuery = true
    )
    Page<ProductModel> findActiveSortByLikesDesc(@Param("refBrandId") Long refBrandId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE products SET stock_quantity = stock_quantity - :quantity WHERE id = :productId AND stock_quantity >= :quantity",
            nativeQuery = true
    )
    int decreaseStockIfAvailable(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE products SET stock_quantity = stock_quantity + :quantity WHERE id = :productId",
            nativeQuery = true
    )
    void increaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE products SET like_count = like_count + 1 WHERE id = :productId",
            nativeQuery = true
    )
    void incrementLikeCount(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE products SET like_count = like_count - 1 WHERE id = :productId AND like_count > 0",
            nativeQuery = true
    )
    void decrementLikeCount(@Param("productId") Long productId);

    @Query(
            value = "SELECT * FROM products WHERE ref_brand_id = :brandId AND deleted_at IS NULL",
            nativeQuery = true
    )
    List<ProductModel> findActiveByRefBrandId(@Param("brandId") Long brandId);
}
