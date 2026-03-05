package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponApp;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("쿠폰 발급 동시성 테스트")
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponApp couponApp;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일 사용자가 같은 쿠폰을 10번 동시 발급 시도 → UNIQUE 제약으로 정확히 1번만 성공")
    void issueCoupon_concurrency_sameUserOnlyOneSucceeds() throws InterruptedException {
        // given
        CouponTemplateModel savedTemplate = couponTemplateRepository.save(CouponTemplateModel.create(
                "동시성테스트쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7)
        ));
        Long couponTemplateId = savedTemplate.getId();
        Long memberId = 1L;

        int concurrentThreads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(concurrentThreads);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);

        // when
        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    couponApp.issueUserCoupon(couponTemplateId, memberId);
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
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(concurrentThreads - 1);
    }
}
