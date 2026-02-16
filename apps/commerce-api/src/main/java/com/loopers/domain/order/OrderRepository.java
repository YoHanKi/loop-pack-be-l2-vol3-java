package com.loopers.domain.order;

import com.loopers.domain.order.vo.OrderId;

import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findByOrderId(OrderId orderId);
}
