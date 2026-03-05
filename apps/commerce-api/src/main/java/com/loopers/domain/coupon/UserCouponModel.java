package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import com.loopers.infrastructure.jpa.converter.RefCouponTemplateIdConverter;
import com.loopers.infrastructure.jpa.converter.RefMemberIdConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "user_coupons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_coupon_member_template",
                        columnNames = {"ref_member_id", "ref_coupon_template_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel extends BaseEntity {

    @Convert(converter = RefMemberIdConverter.class)
    @Column(name = "ref_member_id", nullable = false)
    private RefMemberId refMemberId;

    @Convert(converter = RefCouponTemplateIdConverter.class)
    @Column(name = "ref_coupon_template_id", nullable = false)
    private RefCouponTemplateId refCouponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private UserCouponStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    private UserCouponModel(Long refMemberId, Long refCouponTemplateId) {
        this.refMemberId = new RefMemberId(refMemberId);
        this.refCouponTemplateId = new RefCouponTemplateId(refCouponTemplateId);
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
