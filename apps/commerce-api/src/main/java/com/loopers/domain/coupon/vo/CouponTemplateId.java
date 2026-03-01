package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.UUID;

public record CouponTemplateId(String value) {

    public CouponTemplateId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponTemplateId가 비어 있습니다");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponTemplateId는 UUID 형식이어야 합니다: " + value);
        }
    }

    public static CouponTemplateId generate() {
        return new CouponTemplateId(UUID.randomUUID().toString());
    }
}
