package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponApp;
import com.loopers.domain.coupon.vo.UserCouponId;
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
@DisplayName("쿠폰 사용 동시성 테스트")
class CouponUseConcurrencyTest {

    @Autowired
    private CouponApp couponApp;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("1개 UserCoupon을 10개 스레드가 동시 사용 시도 → 정확히 1개만 성공")
    void useUserCoupon_concurrency_onlyOneSucceeds() throws InterruptedException {
        // given
        CouponTemplateModel template = CouponTemplateModel.create(
                "사용동시성쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7), 100
        );
        couponTemplateRepository.save(template);

        UserCouponModel userCoupon = UserCouponModel.create(1L, template.getId());
        userCouponRepository.save(userCoupon);
        String userCouponIdValue = userCoupon.getUserCouponId().value();

        int concurrentThreads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(concurrentThreads);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);

        // when
        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    couponApp.useUserCoupon(userCouponIdValue);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    conflictCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(concurrentThreads - 1);

        UserCouponModel updated = userCouponRepository.findByUserCouponId(
                new UserCouponId(userCouponIdValue)).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserCouponStatus.USED);
    }
}
