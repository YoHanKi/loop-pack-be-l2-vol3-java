package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.vo.UserCouponId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class UserCouponIdConverter implements AttributeConverter<UserCouponId, String> {

    @Override
    public String convertToDatabaseColumn(UserCouponId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public UserCouponId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new UserCouponId(dbData);
    }
}
