package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponAdminV1Dto {

    public record CreateTemplateRequest(
            @NotBlank(message = "쿠폰 이름은 필수입니다.")
            String name,

            @NotNull(message = "쿠폰 타입은 필수입니다.")
            CouponType type,

            @NotNull(message = "할인 값은 필수입니다.")
            BigDecimal value,

            BigDecimal minOrderAmount,

            @NotNull(message = "만료일은 필수입니다.")
            ZonedDateTime expiredAt
    ) {}

    public record UpdateTemplateRequest(
            @NotBlank(message = "쿠폰 이름은 필수입니다.")
            String name,

            @NotNull(message = "할인 값은 필수입니다.")
            BigDecimal value,

            BigDecimal minOrderAmount,

            @NotNull(message = "만료일은 필수입니다.")
            ZonedDateTime expiredAt
    ) {}

    public record TemplateResponse(
            Long id,
            String name,
            String type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static TemplateResponse from(CouponTemplateInfo info) {
            return new TemplateResponse(
                    info.id(),
                    info.name(),
                    info.type().name(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt()
            );
        }
    }

    public record IssueListResponse(
            List<UserCouponItem> content,
            int size
    ) {
        public static IssueListResponse from(List<UserCouponInfo> infos) {
            List<UserCouponItem> items = infos.stream()
                    .map(UserCouponItem::from)
                    .toList();
            return new IssueListResponse(items, items.size());
        }
    }

    public record UserCouponItem(
            Long id,
            Long refMemberId,
            String status,
            ZonedDateTime createdAt
    ) {
        public static UserCouponItem from(UserCouponInfo info) {
            return new UserCouponItem(
                    info.id(),
                    info.refMemberId(),
                    info.status(),
                    info.createdAt()
            );
        }
    }
}
