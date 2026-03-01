package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

import java.math.BigDecimal;

public record OrderItemInfo(
            Long id,
            String orderItemId,
            String productId,
            String productName,
            BigDecimal price,
            int quantity,
            BigDecimal totalPrice
    ) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(
                    item.getId(),
                    item.getOrderItemId().value(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getPrice(),
                    item.getQuantity(),
                    item.getTotalPrice()
            );
        }
    }
