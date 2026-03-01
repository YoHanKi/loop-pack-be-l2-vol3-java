package com.loopers.domain.coupon;

import com.loopers.domain.coupon.vo.CouponTemplateId;
import com.loopers.domain.coupon.vo.UserCouponId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponTemplateModel createTemplate(String name, CouponType type, BigDecimal value,
                                              BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                              int totalQuantity) {
        CouponTemplateModel template = CouponTemplateModel.create(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return couponTemplateRepository.save(template);
    }

    public CouponTemplateModel updateTemplate(String couponTemplateIdValue, String name, BigDecimal value,
                                              BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                              int totalQuantity) {
        CouponTemplateModel template = findActiveTemplate(couponTemplateIdValue);
        template.update(name, value, minOrderAmount, expiredAt, totalQuantity);
        return couponTemplateRepository.save(template);
    }

    public void deleteTemplate(String couponTemplateIdValue) {
        CouponTemplateModel template = findActiveTemplate(couponTemplateIdValue);
        template.markAsDeleted();
        couponTemplateRepository.save(template);
    }

    public UserCouponModel issueUserCoupon(String couponTemplateIdValue, Long memberId) {
        CouponTemplateId couponTemplateId = new CouponTemplateId(couponTemplateIdValue);

        // 비관적 락으로 템플릿 조회
        CouponTemplateModel template = couponTemplateRepository.findByCouponTemplateIdForUpdate(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));

        if (template.isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다.");
        }

        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        if (template.getIssuedQuantity() >= template.getTotalQuantity()) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
        }

        boolean alreadyIssued = userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(
                memberId, template.getId());
        if (alreadyIssued) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        template.incrementIssuedQuantity();
        couponTemplateRepository.save(template);

        UserCouponModel userCoupon = UserCouponModel.create(memberId, template.getId());
        return userCouponRepository.save(userCoupon);
    }

    public BigDecimal calculateDiscount(String userCouponIdValue, Long memberId, BigDecimal originalAmount) {
        UserCouponId userCouponId = new UserCouponId(userCouponIdValue);
        UserCouponModel userCoupon = userCouponRepository.findByUserCouponId(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 쿠폰이 존재하지 않습니다."));

        if (!userCoupon.getRefMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 소유의 쿠폰만 사용할 수 있습니다.");
        }

        if (!userCoupon.isAvailable()) {
            throw new CoreException(ErrorType.CONFLICT, "사용 가능한 쿠폰이 아닙니다.");
        }

        CouponTemplateModel template = couponTemplateRepository.findByPkId(userCoupon.getRefCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));

        if (userCoupon.isExpired(template.getExpiredAt())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        if (template.getMinOrderAmount() != null &&
                originalAmount.compareTo(template.getMinOrderAmount()) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "최소 주문금액을 충족하지 못합니다. 최소: " + template.getMinOrderAmount());
        }

        if (template.getType() == CouponType.FIXED) {
            BigDecimal discount = template.getValue();
            return discount.compareTo(originalAmount) > 0 ? originalAmount : discount;
        } else {
            return originalAmount.multiply(template.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        }
    }

    public Long useUserCoupon(String userCouponIdValue) {
        UserCouponId userCouponId = new UserCouponId(userCouponIdValue);
        UserCouponModel userCoupon = userCouponRepository.findByUserCouponId(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 쿠폰이 존재하지 않습니다."));

        int rowsAffected = userCouponRepository.useIfAvailable(userCoupon.getId());
        if (rowsAffected == 0) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 이미 사용되었거나 사용할 수 없는 상태입니다.");
        }
        return userCoupon.getId();
    }

    public void restoreUserCouponByPkId(Long pkId) {
        // idempotent: rowsAffected == 0이면 이미 복원된 상태이므로 무시
        userCouponRepository.restoreIfUsed(pkId);
    }

    public CouponTemplateModel findActiveTemplate(String couponTemplateIdValue) {
        CouponTemplateId couponTemplateId = new CouponTemplateId(couponTemplateIdValue);
        CouponTemplateModel template = couponTemplateRepository.findByCouponTemplateId(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
        if (template.isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다.");
        }
        return template;
    }
}
