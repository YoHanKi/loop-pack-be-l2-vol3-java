package com.loopers.application.order;

import com.loopers.application.coupon.CouponApp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderApp orderApp;
    private final CouponApp couponApp;

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items, String userCouponId) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long refUserCouponId = null;

        if (userCouponId != null && !userCouponId.isBlank()) {
            // 원래 주문금액 계산 (재고 차감 전 조회)
            BigDecimal originalAmount = orderApp.calculateOriginalAmount(items);
            // 할인 금액 계산
            discountAmount = couponApp.calculateDiscount(userCouponId, originalAmount);
            // 쿠폰 사용 처리 → PK 반환
            refUserCouponId = couponApp.useUserCoupon(userCouponId);
        }

        return orderApp.createOrder(memberId, items, discountAmount, refUserCouponId);
    }

    @Transactional
    public OrderInfo cancelOrder(Long memberId, String orderId) {
        OrderInfo info = orderApp.cancelOrder(memberId, orderId);
        // 쿠폰이 있었던 경우 복원
        if (info.refUserCouponId() != null) {
            couponApp.restoreUserCoupon(info.refUserCouponId());
        }
        return info;
    }
}
