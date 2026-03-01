package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.vo.CouponTemplateId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class CouponTemplateIdConverter implements AttributeConverter<CouponTemplateId, String> {

    @Override
    public String convertToDatabaseColumn(CouponTemplateId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CouponTemplateId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new CouponTemplateId(dbData);
    }
}
