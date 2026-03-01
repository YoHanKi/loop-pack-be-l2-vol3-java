package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApp;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("OrderV1Controller E2E 테스트")
class OrderV1ControllerE2ETest {

    private static final String ORDERS_URL = "/api/v1/orders";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandService brandService;

    @Autowired
    private OrderApp orderApp;

    @Autowired
    private ProductRepository productRepository;

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

    @DisplayName("GET /api/v1/orders/{orderId} - 주문 상세 조회")
    @Nested
    class GetOrder {

        @Test
        @DisplayName("본인 주문 조회 성공 - 200 OK")
        void getOrder_success_returns200() {
            // given
            Long memberId = 1L;
            OrderInfo order = orderApp.createOrder(memberId, List.of(new OrderItemCommand("prod1", 2)));

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = restTemplate.exchange(
                    ORDERS_URL + "/" + order.orderId() + "?memberId=" + memberId,
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isNotNull(),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(order.orderId()),
                    () -> assertThat(response.getBody().data().refMemberId()).isEqualTo(memberId),
                    () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회 - 404 Not Found")
        void getOrder_notFound_returns404() {
            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ORDERS_URL + "/00000000-0000-0000-0000-000000000001?memberId=1",
                    HttpMethod.GET,
                    null,
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("타인의 주문 조회 - 403 Forbidden")
        void getOrder_notOwner_returns403() {
            // given
            Long ownerId = 1L;
            Long otherMemberId = 2L;
            OrderInfo order = orderApp.createOrder(ownerId, List.of(new OrderItemCommand("prod1", 1)));

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ORDERS_URL + "/" + order.orderId() + "?memberId=" + otherMemberId,
                    HttpMethod.GET,
                    null,
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api/v1/orders - 주문 목록 조회")
    @Nested
    class GetOrders {

        @Test
        @DisplayName("주문 목록 페이징 조회 성공 - 200 OK")
        void getOrders_success_returns200() {
            // given
            Long memberId = 1L;
            orderApp.createOrder(memberId, List.of(new OrderItemCommand("prod1", 1)));
            orderApp.createOrder(memberId, List.of(new OrderItemCommand("prod1", 1)));

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = restTemplate.exchange(
                    ORDERS_URL + "?memberId=" + memberId + "&page=0&size=10",
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2)
            );
        }

        @Test
        @DisplayName("주문이 없는 회원은 빈 목록 반환 - 200 OK")
        void getOrders_noOrders_returnsEmpty() {
            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = restTemplate.exchange(
                    ORDERS_URL + "?memberId=99",
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        @DisplayName("다른 회원의 주문은 포함되지 않음")
        void getOrders_onlyReturnsOwnOrders() {
            // given
            Long memberId1 = 1L;
            Long memberId2 = 2L;
            orderApp.createOrder(memberId1, List.of(new OrderItemCommand("prod1", 1)));
            orderApp.createOrder(memberId2, List.of(new OrderItemCommand("prod1", 1)));

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> response = restTemplate.exchange(
                    ORDERS_URL + "?memberId=" + memberId1,
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
        }
    }
}
