package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record CouponTemplateInfo(
        Long id,
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt
) {
    public static CouponTemplateInfo from(CouponTemplateModel model) {
        return new CouponTemplateInfo(
                model.getId(),
                model.getName(),
                model.getType(),
                model.getValue(),
                model.getMinOrderAmount(),
                model.getExpiredAt()
        );
    }
}
