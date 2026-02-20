package com.loopers.domain.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderItemRequest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("OrderService 주문 조회 통합 테스트")
class OrderServiceQueryIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private com.loopers.domain.product.ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        brandService.createBrand("nike", "Nike");
        com.loopers.domain.product.ProductModel product = com.loopers.domain.product.ProductModel.create(
                "prod1", 1L, "Nike Air", new BigDecimal("100000"), 100);
        productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 상세 조회 - getMyOrder")
    @Nested
    class GetMyOrder {

        @Test
        @DisplayName("본인 주문 조회 성공")
        void getMyOrder_success() {
            // given
            Long memberId = 1L;
            OrderModel order = orderService.createOrder(memberId, List.of(new OrderItemRequest("prod1", 1)));

            // when
            OrderModel found = orderService.getMyOrder(memberId, order.getOrderId().value());

            // then
            assertAll(
                    () -> assertThat(found).isNotNull(),
                    () -> assertThat(found.getOrderId()).isEqualTo(order.getOrderId()),
                    () -> assertThat(found.isOwner(memberId)).isTrue()
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회 시 404 예외")
        void getMyOrder_notFound_throwsException() {
            // when & then
            assertThatThrownBy(() -> orderService.getMyOrder(1L, "00000000-0000-0000-0000-000000000001"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("타인의 주문 조회 시 403 예외")
        void getMyOrder_notOwner_throwsForbidden() {
            // given
            Long ownerId = 1L;
            Long otherMemberId = 2L;
            OrderModel order = orderService.createOrder(ownerId, List.of(new OrderItemRequest("prod1", 1)));

            // when & then
            assertThatThrownBy(() -> orderService.getMyOrder(otherMemberId, order.getOrderId().value()))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("주문 목록 조회 - getMyOrders")
    @Nested
    class GetMyOrders {

        @Test
        @DisplayName("회원 주문 목록 페이징 조회")
        void getMyOrders_success() {
            // given
            Long memberId = 1L;
            orderService.createOrder(memberId, List.of(new OrderItemRequest("prod1", 1)));
            orderService.createOrder(memberId, List.of(new OrderItemRequest("prod1", 1)));

            // when
            Page<OrderModel> orders = orderService.getMyOrders(memberId, null, null, PageRequest.of(0, 10));

            // then
            assertAll(
                    () -> assertThat(orders.getTotalElements()).isEqualTo(2),
                    () -> assertThat(orders.getContent()).hasSize(2)
            );
        }

        @Test
        @DisplayName("다른 회원의 주문은 조회되지 않음")
        void getMyOrders_onlyReturnsOwnOrders() {
            // given
            Long memberId1 = 1L;
            Long memberId2 = 2L;
            orderService.createOrder(memberId1, List.of(new OrderItemRequest("prod1", 1)));
            orderService.createOrder(memberId2, List.of(new OrderItemRequest("prod1", 1)));

            // when
            Page<OrderModel> orders = orderService.getMyOrders(memberId1, null, null, PageRequest.of(0, 10));

            // then
            assertThat(orders.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("주문이 없는 회원은 빈 목록 반환")
        void getMyOrders_noOrders_returnsEmpty() {
            // when
            Page<OrderModel> orders = orderService.getMyOrders(99L, null, null, PageRequest.of(0, 10));

            // then
            assertThat(orders.getContent()).isEmpty();
            assertThat(orders.getTotalElements()).isEqualTo(0);
        }
    }
}
