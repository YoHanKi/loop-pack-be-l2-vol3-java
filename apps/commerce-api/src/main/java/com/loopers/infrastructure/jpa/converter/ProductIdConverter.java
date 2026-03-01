package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.product.vo.ProductId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProductIdConverter implements AttributeConverter<ProductId, String> {

    @Override
    public String convertToDatabaseColumn(ProductId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ProductId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ProductId(dbData);
    }
}
