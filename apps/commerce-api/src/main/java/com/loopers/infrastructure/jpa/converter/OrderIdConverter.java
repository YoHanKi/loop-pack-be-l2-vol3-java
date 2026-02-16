package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.order.vo.OrderId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class OrderIdConverter implements AttributeConverter<OrderId, String> {

    @Override
    public String convertToDatabaseColumn(OrderId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public OrderId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new OrderId(dbData);
    }
}
