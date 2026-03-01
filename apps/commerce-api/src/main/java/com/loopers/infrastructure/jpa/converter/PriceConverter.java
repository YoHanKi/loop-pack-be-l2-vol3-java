package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.product.vo.Price;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter(autoApply = false)
public class PriceConverter implements AttributeConverter<Price, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Price attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Price convertToEntityAttribute(BigDecimal dbData) {
        return dbData == null ? null : new Price(dbData);
    }
}
