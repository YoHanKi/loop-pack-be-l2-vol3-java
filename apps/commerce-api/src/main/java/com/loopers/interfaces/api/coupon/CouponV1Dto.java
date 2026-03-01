package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueRequest(
            @NotNull(message = "회원 ID는 필수입니다.")
            Long memberId
    ) {}

    public record UserCouponResponse(
            Long id,
            String userCouponId,
            Long refMemberId,
            Long refCouponTemplateId,
            String status,
            ZonedDateTime createdAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                    info.id(),
                    info.userCouponId(),
                    info.refMemberId(),
                    info.refCouponTemplateId(),
                    info.status(),
                    info.createdAt()
            );
        }
    }
}
