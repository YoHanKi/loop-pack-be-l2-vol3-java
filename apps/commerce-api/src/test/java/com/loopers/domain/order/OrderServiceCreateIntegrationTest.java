package com.loopers.domain.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static com.loopers.domain.order.OrderService.OrderItemRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("OrderService 주문 생성 통합 테스트")
class OrderServiceCreateIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        // Brand와 Product 생성
        brandService.createBrand("nike", "Nike");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("주문 생성 성공 (재고 감소 확인)")
    void createOrder_success_decreasesStock() {
        // given
        ProductModel product = createProduct("prod1", "Nike Air", new BigDecimal("100000"), 10);
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(new OrderItemRequest("prod1", 3));

        // when
        OrderModel order = orderService.createOrder(memberId, requests);

        // then
        assertThat(order).isNotNull();
        assertThat(order.getOrderId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300000.00")); // 100000 * 3

        // 재고 감소 확인
        ProductModel updatedProduct = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        assertThat(updatedProduct.getStockQuantity().value()).isEqualTo(7); // 10 - 3 = 7
    }

    @Test
    @DisplayName("재고 부족 시 409 Conflict (롤백 확인)")
    void createOrder_insufficientStock_rollback() {
        // given
        ProductModel product = createProduct("prod1", "Nike Air", new BigDecimal("100000"), 5);
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(new OrderItemRequest("prod1", 10)); // 재고보다 많이 요청

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(memberId, requests))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);

        // 재고가 롤백되어 원래대로 유지됨
        ProductModel unchangedProduct = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        assertThat(unchangedProduct.getStockQuantity().value()).isEqualTo(5);
    }

    @Test
    @DisplayName("중복 상품 수량 합산 동작")
    void createOrder_aggregateDuplicates() {
        // given
        ProductModel product = createProduct("prod1", "Nike Air", new BigDecimal("100000"), 10);
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(
                new OrderItemRequest("prod1", 2),
                new OrderItemRequest("prod1", 3) // 동일 상품
        );

        // when
        OrderModel order = orderService.createOrder(memberId, requests);

        // then
        assertThat(order.getOrderItems()).hasSize(1); // 하나로 합쳐짐
        assertThat(order.getOrderItems().get(0).getQuantity()).isEqualTo(5); // 2+3=5

        // 재고 감소 확인
        ProductModel updatedProduct = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        assertThat(updatedProduct.getStockQuantity().value()).isEqualTo(5); // 10 - 5 = 5
    }

    @Test
    @DisplayName("존재하지 않는 상품 404")
    void createOrder_productNotFound() {
        // given
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(new OrderItemRequest("invalid", 1));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(memberId, requests))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("여러 상품 주문 시 재고 감소 확인")
    void createOrder_multipleProducts() {
        // given
        ProductModel product1 = createProduct("prod1", "Nike Air", new BigDecimal("100000"), 10);
        ProductModel product2 = createProduct("prod2", "Nike Jordan", new BigDecimal("200000"), 5);
        Long memberId = 1L;
        List<OrderItemRequest> requests = List.of(
                new OrderItemRequest("prod1", 2),
                new OrderItemRequest("prod2", 3)
        );

        // when
        OrderModel order = orderService.createOrder(memberId, requests);

        // then
        assertThat(order.getOrderItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("800000.00")); // (100000*2) + (200000*3)

        // 재고 감소 확인
        ProductModel updatedProduct1 = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        ProductModel updatedProduct2 = productRepository.findByProductId(new ProductId("prod2")).orElseThrow();
        assertThat(updatedProduct1.getStockQuantity().value()).isEqualTo(8); // 10 - 2
        assertThat(updatedProduct2.getStockQuantity().value()).isEqualTo(2); // 5 - 3
    }

    private ProductModel createProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = ProductModel.create(productId, 1L, productName, price, stockQuantity);
        return productRepository.save(product);
    }
}
