package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.product.vo.ProductName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProductNameConverter implements AttributeConverter<ProductName, String> {

    @Override
    public String convertToDatabaseColumn(ProductName attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ProductName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ProductName(dbData);
    }
}
