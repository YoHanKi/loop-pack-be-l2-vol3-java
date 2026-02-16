package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
        // JPQL 쿼리 작성
        StringBuilder jpql = new StringBuilder("SELECT p FROM ProductModel p WHERE p.deletedAt IS NULL");

        // 브랜드 필터
        if (refBrandId != null) {
            jpql.append(" AND p.refBrandId = :refBrandId");
        }

        // 정렬
        jpql.append(getSortClause(sortBy));

        // 쿼리 실행
        TypedQuery<ProductModel> query = entityManager.createQuery(jpql.toString(), ProductModel.class);
        if (refBrandId != null) {
            query.setParameter("refBrandId", refBrandId);
        }

        // 페이징
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<ProductModel> products = query.getResultList();

        // 전체 개수 조회
        long total = countProducts(refBrandId);

        return new PageImpl<>(products, pageable, total);
    }

    private String getSortClause(String sortBy) {
        if (sortBy == null || "latest".equals(sortBy)) {
            return " ORDER BY p.updatedAt DESC";
        } else if ("price_asc".equals(sortBy)) {
            return " ORDER BY p.price ASC";
        } else if ("likes_desc".equals(sortBy)) {
            // TODO: Like 도메인 구현 후 LEFT JOIN + COUNT 서브쿼리로 구현
            throw new UnsupportedOperationException("likes_desc 정렬은 아직 구현되지 않았습니다. Like 도메인 구현 후 추가됩니다.");
        }
        return " ORDER BY p.updatedAt DESC"; // 기본값
    }

    private long countProducts(Long refBrandId) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL");

        if (refBrandId != null) {
            jpql.append(" AND p.refBrandId = :refBrandId");
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        if (refBrandId != null) {
            query.setParameter("refBrandId", refBrandId);
        }

        return query.getSingleResult();
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
