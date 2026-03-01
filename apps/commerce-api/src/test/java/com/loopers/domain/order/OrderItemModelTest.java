package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderItemModel Entity")
class OrderItemModelTest {

    @Test
    @DisplayName("create() 정적 팩토리로 OrderItemModel 생성 성공")
    void create_orderItem_success() {
        // given
        String productId = "prod1";
        String productName = "Test Product";
        BigDecimal price = new BigDecimal("10000");
        int quantity = 3;

        // when
        OrderItemModel item = OrderItemModel.create(productId, productName, price, quantity);

        // then
        assertThat(item).isNotNull();
        assertThat(item.getOrderItemId()).isNotNull();
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getProductName()).isEqualTo(productName);
        assertThat(item.getPrice()).isEqualByComparingTo(price);
        assertThat(item.getQuantity()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("getTotalPrice() = price * quantity")
    void getTotalPrice() {
        // given
        OrderItemModel item = OrderItemModel.create(
                "prod1",
                "Test Product",
                new BigDecimal("10000"),
                3
        );

        // when
        BigDecimal totalPrice = item.getTotalPrice();

        // then
        assertThat(totalPrice).isEqualByComparingTo(new BigDecimal("30000"));
    }
}
