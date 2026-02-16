package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final EntityManager entityManager;

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
        List<ProductModel> products;

        // VO 타입 문제로 Native Query 사용
        if ("likes_desc".equals(sortBy)) {
            products = findProductsWithLikesCount(refBrandId, pageable);
        } else {
            products = findProductsNative(refBrandId, sortBy, pageable);
        }

        // 전체 개수 조회
        long total = countProducts(refBrandId);

        return new PageImpl<>(products, pageable, total);
    }

    private List<ProductModel> findProductsNative(Long refBrandId, String sortBy, Pageable pageable) {
        // Native Query 사용 (VO 타입 문제 회피)
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE deleted_at IS NULL");

        if (refBrandId != null) {
            sql.append(" AND ref_brand_id = :refBrandId");
        }

        sql.append(getNativeSortClause(sortBy));

        var query = entityManager.createNativeQuery(sql.toString(), ProductModel.class);

        if (refBrandId != null) {
            query.setParameter("refBrandId", refBrandId);
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    private String getNativeSortClause(String sortBy) {
        if (sortBy == null || "latest".equals(sortBy)) {
            return " ORDER BY updated_at DESC";
        } else if ("price_asc".equals(sortBy)) {
            return " ORDER BY price ASC";
        }
        return " ORDER BY updated_at DESC"; // 기본값
    }

    private List<ProductModel> findProductsWithLikesCount(Long refBrandId, Pageable pageable) {
        // Native Query: LEFT JOIN으로 좋아요 수 카운트 후 정렬
        StringBuilder sql = new StringBuilder(
                "SELECT p.* FROM products p " +
                "LEFT JOIN likes l ON p.id = l.ref_product_id " +
                "WHERE p.deleted_at IS NULL");

        if (refBrandId != null) {
            sql.append(" AND p.ref_brand_id = :refBrandId");
        }

        sql.append(" GROUP BY p.id")
           .append(" ORDER BY COUNT(l.id) DESC, p.updated_at DESC"); // 좋아요 수 동일 시 최신순

        var query = entityManager.createNativeQuery(sql.toString(), ProductModel.class);

        if (refBrandId != null) {
            query.setParameter("refBrandId", refBrandId);
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    private long countProducts(Long refBrandId) {
        // Native Query 사용 (VO 타입 문제 회피)
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products WHERE deleted_at IS NULL");

        if (refBrandId != null) {
            sql.append(" AND ref_brand_id = :refBrandId");
        }

        var query = entityManager.createNativeQuery(sql.toString());
        if (refBrandId != null) {
            query.setParameter("refBrandId", refBrandId);
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public boolean decreaseStockIfAvailable(Long productId, int quantity) {
        // 동시성 제어를 위한 조건부 UPDATE (Native Query 사용 - VO 타입 문제 회피)
        String sql = "UPDATE products " +
                "SET stock_quantity = stock_quantity - :quantity " +
                "WHERE id = :productId " +
                "AND stock_quantity >= :quantity";

        int updatedCount = entityManager.createNativeQuery(sql)
                .setParameter("quantity", quantity)
                .setParameter("productId", productId)
                .executeUpdate();

        return updatedCount > 0;
    }

    @Override
    public void increaseStock(Long productId, int quantity) {
        // Native Query 사용 (VO 타입 문제 회피)
        String sql = "UPDATE products " +
                "SET stock_quantity = stock_quantity + :quantity " +
                "WHERE id = :productId";

        entityManager.createNativeQuery(sql)
                .setParameter("quantity", quantity)
                .setParameter("productId", productId)
                .executeUpdate();
    }
}
