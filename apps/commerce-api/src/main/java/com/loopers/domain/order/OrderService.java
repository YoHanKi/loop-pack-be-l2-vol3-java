package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderModel createOrder(Long memberId, List<OrderItemRequest> itemRequests) {
        // 1. 중복 상품 수량 합산
        Map<String, Integer> aggregatedItems = aggregateQuantities(itemRequests);

        // 2. 상품 ID 정렬 (데드락 방지)
        List<String> sortedProductIds = aggregatedItems.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        // 3. 상품 조회 및 재고 차감
        List<OrderItemModel> orderItems = new ArrayList<>();
        for (String productIdValue : sortedProductIds) {
            int quantity = aggregatedItems.get(productIdValue);

            // 상품 조회
            ProductModel product = productRepository.findByProductId(new ProductId(productIdValue))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "해당 ID의 상품이 존재하지 않습니다: " + productIdValue));

            // 재고 차감 (동시성 제어)
            boolean decreased = productRepository.decreaseStockIfAvailable(product.getId(), quantity);
            if (!decreased) {
                throw new CoreException(ErrorType.CONFLICT,
                        "재고가 부족합니다. 상품 ID: " + productIdValue);
            }

            // OrderItemModel 생성 (스냅샷 패턴)
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

    private Map<String, Integer> aggregateQuantities(List<OrderItemRequest> itemRequests) {
        Map<String, Integer> aggregated = new HashMap<>();
        for (OrderItemRequest request : itemRequests) {
            aggregated.merge(request.productId(), request.quantity(), Integer::sum);
        }
        return aggregated;
    }

    /**
     * 주문 상품 요청 DTO
     */
    public record OrderItemRequest(String productId, int quantity) {
        public OrderItemRequest {
            if (productId == null || productId.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
            }
            if (quantity <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1개 이상이어야 합니다.");
            }
        }
    }
}
