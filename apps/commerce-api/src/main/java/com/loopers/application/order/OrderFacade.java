package com.loopers.application.order;

import com.loopers.domain.order.OrderItemRequest;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items) {
        List<OrderItemRequest> orderItems = items.stream()
                .map(OrderItemCommand::toOrderItemRequest)
                .toList();
        OrderModel order = orderService.createOrder(memberId, orderItems);
        return OrderInfo.from(order);
    }

    @Transactional
    public OrderInfo cancelOrder(Long memberId, String orderId) {
        OrderModel order = orderService.cancelOrder(memberId, orderId);
        return OrderInfo.from(order);
    }
}
