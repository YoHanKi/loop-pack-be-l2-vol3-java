package com.loopers.domain.order.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.UUID;

public record OrderItemId(String value) {

    public OrderItemId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderItemId가 비어 있습니다");
        }
        // UUID 형식 검증
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderItemId는 UUID 형식이어야 합니다: " + value);
        }
    }

    public static OrderItemId generate() {
        return new OrderItemId(UUID.randomUUID().toString());
    }
}
