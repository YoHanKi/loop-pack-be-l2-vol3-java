package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.brand.vo.BrandId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BrandIdConverter implements AttributeConverter<BrandId, String> {

    @Override
    public String convertToDatabaseColumn(BrandId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public BrandId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new BrandId(dbData);
    }
}
