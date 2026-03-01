package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.vo.CouponTemplateId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponApp {

    private final CouponService couponService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponTemplateInfo createTemplate(String name, CouponType type, BigDecimal value,
                                             BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                             int totalQuantity) {
        CouponTemplateModel template = couponService.createTemplate(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return CouponTemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getTemplate(String couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findByCouponTemplateId(new CouponTemplateId(couponTemplateId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
        if (template.isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다.");
        }
        return CouponTemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplateInfo> getAllTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable)
                .map(CouponTemplateInfo::from);
    }

    @Transactional
    public CouponTemplateInfo updateTemplate(String couponTemplateId, String name, BigDecimal value,
                                             BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                             int totalQuantity) {
        CouponTemplateModel template = couponService.updateTemplate(couponTemplateId, name, value, minOrderAmount, expiredAt, totalQuantity);
        return CouponTemplateInfo.from(template);
    }

    @Transactional
    public void deleteTemplate(String couponTemplateId) {
        couponService.deleteTemplate(couponTemplateId);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getIssuedCoupons(String couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findByCouponTemplateId(new CouponTemplateId(couponTemplateId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
        List<UserCouponModel> userCoupons = userCouponRepository.findByRefCouponTemplateId(template.getId());
        ZonedDateTime expiredAt = template.getExpiredAt();
        return userCoupons.stream()
                .map(uc -> UserCouponInfo.from(uc, expiredAt))
                .toList();
    }

    @Transactional
    public UserCouponInfo issueUserCoupon(String couponTemplateId, Long memberId) {
        UserCouponModel userCoupon = couponService.issueUserCoupon(couponTemplateId, memberId);
        CouponTemplateModel template = couponTemplateRepository.findByPkId(userCoupon.getRefCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
        return UserCouponInfo.from(userCoupon, template.getExpiredAt());
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyUserCoupons(Long memberId) {
        List<UserCouponModel> userCoupons = userCouponRepository.findByRefMemberId(memberId);
        return userCoupons.stream()
                .map(uc -> {
                    CouponTemplateModel template = couponTemplateRepository.findByPkId(uc.getRefCouponTemplateId())
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
                    return UserCouponInfo.from(uc, template.getExpiredAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateDiscount(String userCouponId, BigDecimal originalAmount) {
        return couponService.calculateDiscount(userCouponId, originalAmount);
    }

    /**
     * 쿠폰을 사용 처리하고 UserCoupon의 PK를 반환한다.
     * OrderFacade에서 refUserCouponId 저장에 사용된다.
     */
    @Transactional
    public Long useUserCoupon(String userCouponId) {
        return couponService.useUserCoupon(userCouponId);
    }

    /**
     * PK 기반으로 쿠폰을 AVAILABLE로 복원한다 (주문 취소 시 사용, idempotent).
     */
    @Transactional
    public void restoreUserCoupon(Long userCouponPkId) {
        couponService.restoreUserCouponByPkId(userCouponPkId);
    }
}
