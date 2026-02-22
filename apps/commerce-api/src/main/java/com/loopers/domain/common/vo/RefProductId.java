package com.loopers.domain.common.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record RefProductId(Long value) {

    public RefProductId {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refProductId가 비어 있습니다");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refProductId는 양수여야 합니다: " + value);
        }
    }
}
