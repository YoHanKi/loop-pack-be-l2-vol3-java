package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(
            @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        List<OrderItemCommand> items = request.items().stream()
                .map(OrderV1Dto.OrderItemRequest::toCommand)
                .toList();

        OrderInfo info = orderFacade.createOrder(request.memberId(), items);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(OrderV1Dto.OrderResponse.from(info)));
    }

    @PatchMapping("/{orderId}/cancel")
    @Override
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody OrderV1Dto.CancelOrderRequest request
    ) {
        OrderInfo info = orderFacade.cancelOrder(request.memberId(), orderId);
        return ResponseEntity.ok(ApiResponse.success(OrderV1Dto.OrderResponse.from(info)));
    }
}
