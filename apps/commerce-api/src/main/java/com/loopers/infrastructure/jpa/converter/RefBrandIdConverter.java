package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.product.vo.RefBrandId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RefBrandIdConverter implements AttributeConverter<RefBrandId, Long> {

    @Override
    public Long convertToDatabaseColumn(RefBrandId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RefBrandId convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new RefBrandId(dbData);
    }
}
