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
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        Long refUserCouponId,
        List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
                order.getId(),
                order.getOrderId().value(),
                order.getRefMemberId().value(),
                order.getStatus().name(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getRefUserCouponId(),
                order.getOrderItems().stream()
                        .map(OrderItemInfo::from)
                        .toList()
        );
    }
}
