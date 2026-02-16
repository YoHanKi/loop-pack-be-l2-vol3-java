package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Price(BigDecimal value) {

    public Price {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격이 비어 있습니다");
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다: " + value);
        }

        // scale을 2로 설정 (소수점 2자리, 반올림)
        value = value.setScale(2, RoundingMode.HALF_UP);
    }
}
