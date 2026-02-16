package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record RefBrandId(Long value) {

    public RefBrandId {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refBrandId가 비어 있습니다");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refBrandId는 양수여야 합니다: " + value);
        }
    }
}
