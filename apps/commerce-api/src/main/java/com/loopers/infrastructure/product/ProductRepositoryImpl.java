package com.loopers.infrastructure.product;

import com.loopers.domain.common.vo.RefBrandId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.vo.ProductId;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
    private final JPAQueryFactory queryFactory;

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
        QProductModel product = QProductModel.productModel;

        BooleanExpression condition = product.deletedAt.isNull();
        if (refBrandId != null) {
            condition = condition.and(product.refBrandId.eq(new RefBrandId(refBrandId)));
        }

        ProductSortCondition sortCondition = ProductSortCondition.from(sortBy);

        List<ProductModel> content = queryFactory
                .selectFrom(product)
                .where(condition)
                .orderBy(sortCondition.toOrderSpecifiers(product))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
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
    public void incrementLikeCount(Long productId) {
        productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        productJpaRepository.decrementLikeCount(productId);
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
