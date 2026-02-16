package com.loopers.domain.order;

import com.loopers.domain.order.vo.OrderId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderReader {
    private final OrderRepository orderRepository;

    public OrderModel getOrThrow(String orderId) {
        return orderRepository.findByOrderId(new OrderId(orderId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 주문이 존재하지 않습니다."));
    }
}
