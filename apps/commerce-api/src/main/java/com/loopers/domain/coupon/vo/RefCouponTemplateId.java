package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record RefCouponTemplateId(Long value) {

    public RefCouponTemplateId {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refCouponTemplateId가 비어 있습니다");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refCouponTemplateId는 양수여야 합니다: " + value);
        }
    }
}
