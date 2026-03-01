package com.loopers.domain.product;


import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductModel Entity")
class ProductModelTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {
        @Test
        @DisplayName("create() 정적 팩토리로 ProductModel 생성 성공")
        void create_product_model() {
        // given
        String productId = "prod1";
        String brandId = "nike";
        String productName = "Nike Air Max";
        BigDecimal price = new BigDecimal("150000");
        int stockQuantity = 100;

        // when
        ProductModel product = ProductModel.create(productId, 1L, productName, price, stockQuantity);

        // then
        assertThat(product.getProductId()).isEqualTo(new ProductId(productId));
        assertThat(product.getProductName()).isEqualTo(new ProductName(productName));
        assertThat(product.getPrice().value()).isEqualByComparingTo(price.setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(product.getStockQuantity().value()).isEqualTo(stockQuantity);
        assertThat(product.isDeleted()).isFalse();
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DecreaseStock {
        @Test
        @DisplayName("재고가 충분하면 차감 성공")
        void decreaseStock_success() {
        // given
        ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 50);

        // when
        product.decreaseStock(10);

        // then
        assertThat(product.getStockQuantity().value()).isEqualTo(40);
        }

        @Test
        @DisplayName("재고가 부족하면 예외 발생")
        void decreaseStock_insufficient_stock_throws_exception() {
        // given
        ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 5);

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(10))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("0개 차감 시 재고 변화 없음")
        void decreaseStock_zero_does_not_change_stock() {
        // given
        ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 50);

        // when
        product.decreaseStock(0);

        // then
        assertThat(product.getStockQuantity().value()).isEqualTo(50);
        }
    }

    @DisplayName("재고를 증가할 때,")
    @Nested
    class IncreaseStock {
        @Test
        @DisplayName("재고 증가 성공")
        void increaseStock_success() {
            // given
            ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 50);

            // when
            product.increaseStock(20);

            // then
            assertThat(product.getStockQuantity().value()).isEqualTo(70);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {
        @Test
        @DisplayName("markAsDeleted() 호출 시 deletedAt 설정됨")
        void mark_as_deleted_sets_deletedAt() {
            // given
            ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 50);
            assertThat(product.isDeleted()).isFalse();

            // when
            product.markAsDeleted();

            // then
            assertThat(product.isDeleted()).isTrue();
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }
}
