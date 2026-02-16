package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.product.vo.StockQuantity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StockQuantityConverter implements AttributeConverter<StockQuantity, Integer> {

    @Override
    public Integer convertToDatabaseColumn(StockQuantity attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public StockQuantity convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new StockQuantity(dbData);
    }
}
