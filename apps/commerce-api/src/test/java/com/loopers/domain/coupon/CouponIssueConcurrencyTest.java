package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponApp;
import com.loopers.domain.coupon.vo.CouponTemplateId;
import com.loopers.support.error.CoreException;
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
    @DisplayName("totalQuantity=5인 쿠폰을 10명이 동시 발급 시도 → 정확히 5명만 성공")
    void issueCoupon_concurrency_onlyTotalQuantitySucceeds() throws InterruptedException {
        // given
        int totalQuantity = 5;
        int concurrentUsers = 10;
        CouponTemplateModel template = CouponTemplateModel.create(
                "동시성테스트쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7), totalQuantity
        );
        couponTemplateRepository.save(template);
        String couponTemplateIdValue = template.getCouponTemplateId().value();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            final long memberId = i + 1L;
            executor.submit(() -> {
                try {
                    couponApp.issueUserCoupon(couponTemplateIdValue, memberId);
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
        assertThat(successCount.get()).isEqualTo(totalQuantity);
        assertThat(failCount.get()).isEqualTo(concurrentUsers - totalQuantity);

        CouponTemplateModel updated = couponTemplateRepository.findByCouponTemplateId(
                new CouponTemplateId(couponTemplateIdValue)).orElseThrow();
        assertThat(updated.getIssuedQuantity()).isEqualTo(totalQuantity);
    }
}
