package com.loopers.domain.product;

import com.loopers.application.order.OrderApp;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("재고 차감 동시성 테스트")
class StockConcurrencyTest {

    @Autowired
    private OrderApp orderApp;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        brandService.createBrand("nike", "Nike");
        ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 5);
        productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("stockQuantity=5인 상품을 10개 스레드가 동시 주문 → 정확히 5개 성공, stockQuantity=0")
    void createOrder_concurrency_stockNeverNegative() throws InterruptedException {
        // given
        int concurrentThreads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(concurrentThreads);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);

        // when
        for (int i = 0; i < concurrentThreads; i++) {
            final long memberId = i + 1L;
            executor.submit(() -> {
                try {
                    orderApp.createOrder(memberId, List.of(new OrderItemCommand("prod1", 1)));
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        ProductModel updatedProduct = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        assertThat(updatedProduct.getStockQuantity().value()).isEqualTo(0);
    }
}
