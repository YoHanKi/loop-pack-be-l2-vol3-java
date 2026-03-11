package com.loopers.infrastructure.product;

import com.loopers.domain.product.QProductModel;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;

import java.math.BigDecimal;

public enum ProductCursorCondition {

    LATEST {
        @Override
        public BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c) {
            return p.updatedAt.lt(c.updatedAt())
                    .or(p.updatedAt.eq(c.updatedAt()).and(p.id.lt(c.id())));
        }

        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p) {
            return new OrderSpecifier<?>[] { p.updatedAt.desc(), p.id.desc() };
        }
    },

    LIKES_DESC {
        @Override
        public BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c) {
            return p.likeCount.lt(c.likeCount())
                    .or(p.likeCount.eq(c.likeCount()).and(p.updatedAt.lt(c.updatedAt())))
                    .or(p.likeCount.eq(c.likeCount())
                            .and(p.updatedAt.eq(c.updatedAt()))
                            .and(p.id.lt(c.id())));
        }

        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p) {
            return new OrderSpecifier<?>[] { p.likeCount.desc(), p.updatedAt.desc(), p.id.desc() };
        }
    },

    PRICE_ASC {
        @Override
        public BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c) {
            NumberPath<BigDecimal> pricePath = Expressions.numberPath(BigDecimal.class, p, "price");
            return pricePath.gt(c.price())
                    .or(pricePath.eq(c.price()).and(p.id.gt(c.id())));
        }

        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p) {
            NumberPath<BigDecimal> pricePath = Expressions.numberPath(BigDecimal.class, p, "price");
            return new OrderSpecifier<?>[] { pricePath.asc(), p.id.asc() };
        }
    };

    public abstract BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c);

    public abstract OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p);

    public static ProductCursorCondition from(String sortBy) {
        return switch (sortBy) {
            case "likes_desc" -> LIKES_DESC;
            case "price_asc"  -> PRICE_ASC;
            default           -> LATEST;
        };
    }

    public static ProductCursorCondition fromType(String cursorType) {
        return switch (cursorType) {
            case "LIKES_DESC" -> LIKES_DESC;
            case "PRICE_ASC"  -> PRICE_ASC;
            default           -> LATEST;
        };
    }
}