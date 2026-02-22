package com.loopers.domain.common.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record RefMemberId(Long value) {

    public RefMemberId {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refMemberId가 비어 있습니다");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refMemberId는 양수여야 합니다: " + value);
        }
    }
}
