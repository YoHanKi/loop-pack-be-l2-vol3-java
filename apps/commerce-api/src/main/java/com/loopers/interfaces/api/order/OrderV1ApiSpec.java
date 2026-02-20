package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "주문 API", description = "주문 생성 및 취소 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "여러 상품을 포함한 주문을 생성합니다. 재고 차감과 스냅샷 저장이 단일 트랜잭션으로 처리됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (빈 주문, 수량 < 1)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(
            @RequestBody OrderV1Dto.CreateOrderRequest request
    );

    @Operation(summary = "주문 취소", description = "PENDING 상태의 주문을 취소합니다. 이미 취소된 주문은 멱등 성공(200)으로 처리됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인의 주문이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> cancelOrder(
            @Parameter(description = "주문 ID (UUID)") @PathVariable String orderId,
            @RequestBody OrderV1Dto.CancelOrderRequest request
    );
}
