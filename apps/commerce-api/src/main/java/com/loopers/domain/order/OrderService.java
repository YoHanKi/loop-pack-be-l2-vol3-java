package com.loopers.domain.order;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.order.vo.OrderId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderModel createOrder(Long memberId, List<OrderItemRequest> itemRequests) {
        // 1. 중복 상품 수량 합산
        Map<String, Integer> aggregatedItems = aggregateQuantities(itemRequests);

        // 2. 상품 ID 정렬 (데드락 방지)
        List<String> sortedProductIds = aggregatedItems.keySet().stream()
                .sorted()
                .toList();

        // 3. 상품 조회 및 재고 차감
        List<OrderItemModel> orderItems = new ArrayList<>();
        for (String productIdValue : sortedProductIds) {
            int quantity = aggregatedItems.get(productIdValue);

            ProductModel product = productRepository.findByProductId(new ProductId(productIdValue))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "해당 ID의 상품이 존재하지 않습니다: " + productIdValue));

            boolean decreased = productRepository.decreaseStockIfAvailable(product.getId(), quantity);
            if (!decreased) {
                throw new CoreException(ErrorType.CONFLICT,
                        "재고가 부족합니다. 상품 ID: " + productIdValue);
            }

            OrderItemModel orderItem = OrderItemModel.create(
                    product.getProductId().value(),
                    product.getProductName().value(),
                    product.getPrice().value(),
                    quantity
            );
            orderItems.add(orderItem);
        }

        // 4. OrderModel 생성 및 저장
        OrderModel order = OrderModel.create(memberId, orderItems);
        return orderRepository.save(order);
    }

    public OrderModel cancelOrder(Long memberId, String orderId) {
        OrderModel order = orderRepository.findByOrderId(new OrderId(orderId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 주문이 존재하지 않습니다."));

        if (!order.isOwner(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 취소할 수 있습니다.");
        }

        OrderStatus previousStatus = order.getStatus();
        order.cancel();

        if (previousStatus == OrderStatus.PENDING) {
            for (OrderItemModel item : order.getOrderItems()) {
                productRepository.findByProductId(new ProductId(item.getProductId()))
                        .ifPresent(product ->
                                productRepository.increaseStock(product.getId(), item.getQuantity())
                        );
            }
        }

        return orderRepository.save(order);
    }

    public Page<OrderModel> getMyOrders(Long memberId, LocalDateTime startDateTime, LocalDateTime endDateTime, Pageable pageable) {
        return orderRepository.findByRefMemberId(new RefMemberId(memberId), startDateTime, endDateTime, pageable);
    }

    public OrderModel getMyOrder(Long memberId, String orderId) {
        OrderModel order = orderRepository.findByOrderId(new OrderId(orderId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwner(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
        return order;
    }

    private Map<String, Integer> aggregateQuantities(List<OrderItemRequest> itemRequests) {
        Map<String, Integer> aggregated = new HashMap<>();
        for (OrderItemRequest request : itemRequests) {
            aggregated.merge(request.productId(), request.quantity(), Integer::sum);
        }
        return aggregated;
    }
}
