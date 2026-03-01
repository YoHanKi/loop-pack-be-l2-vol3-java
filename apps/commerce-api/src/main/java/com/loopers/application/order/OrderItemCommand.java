package com.loopers.application.order;

import com.loopers.domain.order.OrderItemRequest;

public record OrderItemCommand(String productId, int quantity) {

    public OrderItemRequest toOrderItemRequest() {
        return new OrderItemRequest(productId, quantity);
    }
}
