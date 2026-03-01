package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.order.vo.OrderItemId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class OrderItemIdConverter implements AttributeConverter<OrderItemId, String> {

    @Override
    public String convertToDatabaseColumn(OrderItemId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public OrderItemId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new OrderItemId(dbData);
    }
}
