package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record ProductId(String value) {

    // 영문+숫자, 1~20자
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,20}$");

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId가 비어 있습니다");
        }
        value = value.trim();

        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 영문+숫자, 1~20자로 이루어져야 합니다: " + value);
        }
    }
}
