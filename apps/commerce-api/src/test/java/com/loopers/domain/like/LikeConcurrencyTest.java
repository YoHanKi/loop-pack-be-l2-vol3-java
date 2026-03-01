package com.loopers.domain.like;

import com.loopers.application.like.LikeApp;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("좋아요 동시성 테스트")
class LikeConcurrencyTest {

    @Autowired
    private LikeApp likeApp;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        brandService.createBrand("nike", "Nike");
        ProductModel product = ProductModel.create("prod1", 1L, "Nike Air", new BigDecimal("100000"), 100);
        productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("10명이 같은 상품에 동시 좋아요 → 모두 성공, count=10")
    void addLike_concurrency_allSucceed() throws InterruptedException {
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
                    likeApp.addLike(memberId, "prod1");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(concurrentThreads);
        assertThat(failCount.get()).isEqualTo(0);

        ProductModel updatedProduct = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        for (long memberId = 1; memberId <= concurrentThreads; memberId++) {
            RefMemberId refMemberId = new RefMemberId(memberId);
            RefProductId refProductId = new RefProductId(updatedProduct.getId());
            assertThat(likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)).isPresent();
        }
    }
}
