package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.common.vo.RefProductId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RefProductIdConverter implements AttributeConverter<RefProductId, Long> {

    @Override
    public Long convertToDatabaseColumn(RefProductId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RefProductId convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new RefProductId(dbData);
    }
}
