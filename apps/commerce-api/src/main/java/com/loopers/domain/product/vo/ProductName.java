package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record ProductName(String value) {

    public ProductName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명이 비어 있습니다");
        }
        value = value.trim();

        if (value.isEmpty() || value.length() > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 길이는 1자 이상 100자 이하여야 합니다: " + value.length());
        }
    }
}
