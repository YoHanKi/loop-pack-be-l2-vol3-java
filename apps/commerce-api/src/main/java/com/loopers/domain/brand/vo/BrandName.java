package com.loopers.domain.brand.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record BrandName(String value) {

    public BrandName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명이 비어 있습니다");
        }
        value = value.trim();

        if (value.isEmpty() || value.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 길이는 1자 이상 50자 이하여야 합니다: " + value.length());
        }
    }
}
