package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.math.BigDecimal;
import java.util.List;

public record OrderInfo(
        Long id,
        String orderId,
        Long refMemberId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
                order.getId(),
                order.getOrderId().value(),
                order.getRefMemberId().value(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getOrderItems().stream()
                        .map(OrderItemInfo::from)
                        .toList()
        );
    }
}
