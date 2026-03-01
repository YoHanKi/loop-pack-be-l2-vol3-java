package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;

public record UserCouponInfo(
        Long id,
        String userCouponId,
        Long refMemberId,
        Long refCouponTemplateId,
        String status,
        ZonedDateTime createdAt
) {
    public static UserCouponInfo from(UserCouponModel model, ZonedDateTime templateExpiredAt) {
        String resolvedStatus;
        if (model.isExpired(templateExpiredAt)) {
            resolvedStatus = "EXPIRED";
        } else {
            resolvedStatus = model.getStatus().name();
        }
        return new UserCouponInfo(
                model.getId(),
                model.getUserCouponId().value(),
                model.getRefMemberId(),
                model.getRefCouponTemplateId(),
                resolvedStatus,
                model.getCreatedAt()
        );
    }
}
