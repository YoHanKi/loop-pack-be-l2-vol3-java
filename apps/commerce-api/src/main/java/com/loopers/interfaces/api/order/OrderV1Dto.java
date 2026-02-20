package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderItemInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
            @NotNull(message = "회원 ID는 필수입니다.")
            Long memberId,

            @NotNull(message = "주문 항목은 필수입니다.")
            @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.")
            @Valid
            List<OrderItemRequest> items
    ) {}

    public record OrderItemRequest(
            @NotBlank(message = "상품 ID는 필수입니다.")
            String productId,

            @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
            int quantity
    ) {
        public OrderItemCommand toCommand() {
            return new OrderItemCommand(productId, quantity);
        }
    }

    public record CancelOrderRequest(
            @NotNull(message = "회원 ID는 필수입니다.")
            Long memberId
    ) {}

    public record OrderResponse(
            Long id,
            String orderId,
            Long refMemberId,
            String status,
            BigDecimal totalAmount,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.orderId(),
                    info.refMemberId(),
                    info.status(),
                    info.totalAmount(),
                    info.items().stream()
                            .map(OrderItemResponse::from)
                            .toList()
            );
        }
    }

    public record OrderItemResponse(
            Long id,
            String orderItemId,
            String productId,
            String productName,
            BigDecimal price,
            int quantity,
            BigDecimal totalPrice
    ) {
        public static OrderItemResponse from(OrderItemInfo item) {
            return new OrderItemResponse(
                    item.id(),
                    item.orderItemId(),
                    item.productId(),
                    item.productName(),
                    item.price(),
                    item.quantity(),
                    item.totalPrice()
            );
        }
    }
}
