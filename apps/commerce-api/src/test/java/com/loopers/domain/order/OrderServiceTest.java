package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.loopers.domain.order.OrderService.OrderItemRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("createOrder: 주문 생성 성공")
    void createOrder_success() {
        // given
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(
                new OrderItemRequest("prod1", 2),
                new OrderItemRequest("prod2", 3)
        );

        ProductModel product1 = mockProduct("prod1", "Product 1", new BigDecimal("10000"), 100L);
        ProductModel product2 = mockProduct("prod2", "Product 2", new BigDecimal("20000"), 101L);

        when(productRepository.findByProductId(new ProductId("prod1"))).thenReturn(Optional.of(product1));
        when(productRepository.findByProductId(new ProductId("prod2"))).thenReturn(Optional.of(product2));
        when(productRepository.decreaseStockIfAvailable(100L, 2)).thenReturn(true);
        when(productRepository.decreaseStockIfAvailable(101L, 3)).thenReturn(true);

        OrderModel savedOrder = mock(OrderModel.class);
        when(orderRepository.save(any(OrderModel.class))).thenReturn(savedOrder);

        // when
        OrderModel result = orderService.createOrder(memberId, requests);

        // then
        assertThat(result).isEqualTo(savedOrder);
        verify(productRepository).decreaseStockIfAvailable(100L, 2);
        verify(productRepository).decreaseStockIfAvailable(101L, 3);
        verify(orderRepository).save(any(OrderModel.class));
    }

    @Test
    @DisplayName("createOrder: 중복 상품 수량 합산")
    void createOrder_aggregateQuantities() {
        // given
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(
                new OrderItemRequest("prod1", 2),
                new OrderItemRequest("prod1", 3) // 동일 상품
        );

        ProductModel product = mockProduct("prod1", "Product 1", new BigDecimal("10000"), 100L);
        when(productRepository.findByProductId(new ProductId("prod1"))).thenReturn(Optional.of(product));
        when(productRepository.decreaseStockIfAvailable(100L, 5)).thenReturn(true); // 2+3=5

        OrderModel savedOrder = mock(OrderModel.class);
        when(orderRepository.save(any(OrderModel.class))).thenReturn(savedOrder);

        // when
        orderService.createOrder(memberId, requests);

        // then
        verify(productRepository).decreaseStockIfAvailable(100L, 5);
    }

    @Test
    @DisplayName("createOrder: 재고 부족 시 예외 발생")
    void createOrder_insufficientStock_throwsException() {
        // given
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(new OrderItemRequest("prod1", 100));

        // 재고 부족 시에는 OrderItemModel 생성 전에 예외 발생하므로 getId()만 필요
        ProductModel product = mock(ProductModel.class);
        when(product.getId()).thenReturn(100L);
        when(productRepository.findByProductId(new ProductId("prod1"))).thenReturn(Optional.of(product));
        when(productRepository.decreaseStockIfAvailable(100L, 100)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(memberId, requests))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("createOrder: 존재하지 않는 상품 시 예외 발생")
    void createOrder_productNotFound_throwsException() {
        // given
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(new OrderItemRequest("invalid", 1));

        when(productRepository.findByProductId(new ProductId("invalid"))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(memberId, requests))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("OrderItemRequest: 상품 ID null 시 예외 발생")
    void orderItemRequest_nullProductId_throwsException() {
        // when & then
        assertThatThrownBy(() -> new OrderItemRequest(null, 1))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("OrderItemRequest: 수량 0 이하 시 예외 발생")
    void orderItemRequest_invalidQuantity_throwsException() {
        // when & then
        assertThatThrownBy(() -> new OrderItemRequest("prod1", 0))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }

    private ProductModel mockProduct(String productId, String productName, BigDecimal price, Long id) {
        ProductModel product = mock(ProductModel.class);
        when(product.getId()).thenReturn(id);
        when(product.getProductId()).thenReturn(new ProductId(productId));
        when(product.getProductName()).thenReturn(new com.loopers.domain.product.vo.ProductName(productName));
        when(product.getPrice()).thenReturn(new com.loopers.domain.product.vo.Price(price));
        return product;
    }
}
