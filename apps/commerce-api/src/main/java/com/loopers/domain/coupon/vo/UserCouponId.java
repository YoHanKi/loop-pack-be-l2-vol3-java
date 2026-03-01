package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.UUID;

public record UserCouponId(String value) {

    public UserCouponId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userCouponId가 비어 있습니다");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userCouponId는 UUID 형식이어야 합니다: " + value);
        }
    }

    public static UserCouponId generate() {
        return new UserCouponId(UUID.randomUUID().toString());
    }
}
