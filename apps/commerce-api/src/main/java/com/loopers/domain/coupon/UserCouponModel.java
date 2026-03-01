package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.vo.UserCouponId;
import com.loopers.infrastructure.coupon.UserCouponIdConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "user_coupons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_coupon_id", columnNames = {"user_coupon_id"}),
                @UniqueConstraint(name = "uk_user_coupon_member_template",
                        columnNames = {"ref_member_id", "ref_coupon_template_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel extends BaseEntity {

    @Convert(converter = UserCouponIdConverter.class)
    @Column(name = "user_coupon_id", nullable = false, length = 36)
    private UserCouponId userCouponId;

    @Column(name = "ref_member_id", nullable = false)
    private Long refMemberId;

    @Column(name = "ref_coupon_template_id", nullable = false)
    private Long refCouponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private UserCouponStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    private UserCouponModel(Long refMemberId, Long refCouponTemplateId) {
        this.userCouponId = UserCouponId.generate();
        this.refMemberId = refMemberId;
        this.refCouponTemplateId = refCouponTemplateId;
        this.status = UserCouponStatus.AVAILABLE;
        this.version = 0;
    }

    public static UserCouponModel create(Long memberId, Long templateId) {
        return new UserCouponModel(memberId, templateId);
    }

    public boolean isAvailable() {
        return this.status == UserCouponStatus.AVAILABLE;
    }

    public boolean isExpired(ZonedDateTime expiredAt) {
        return ZonedDateTime.now().isAfter(expiredAt);
    }
}
