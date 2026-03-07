package com.loopers.application.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
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
                                             BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplateModel template = couponService.createTemplate(name, type, value, minOrderAmount, expiredAt);
        return CouponTemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getTemplate(Long couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findById(couponTemplateId)
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
    public CouponTemplateInfo updateTemplate(Long couponTemplateId, String name, BigDecimal value,
                                             BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplateModel template = couponService.updateTemplate(couponTemplateId, name, value, minOrderAmount, expiredAt);
        return CouponTemplateInfo.from(template);
    }

    @Transactional
    public void deleteTemplate(Long couponTemplateId) {
        couponService.deleteTemplate(couponTemplateId);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getIssuedCoupons(Long couponTemplateId) {
        CouponTemplateModel template = couponTemplateRepository.findById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
        List<UserCouponModel> userCoupons = userCouponRepository.findByRefCouponTemplateId(new RefCouponTemplateId(template.getId()));
        ZonedDateTime expiredAt = template.getExpiredAt();
        return userCoupons.stream()
                .map(uc -> UserCouponInfo.from(uc, expiredAt))
                .toList();
    }

    @Transactional
    public UserCouponInfo issueUserCoupon(Long couponTemplateId, Long memberId) {
        UserCouponModel userCoupon = couponService.issueUserCoupon(couponTemplateId, memberId);
        CouponTemplateModel template = couponTemplateRepository.findById(userCoupon.getRefCouponTemplateId().value())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
        return UserCouponInfo.from(userCoupon, template.getExpiredAt());
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyUserCoupons(Long memberId) {
        List<UserCouponModel> userCoupons = userCouponRepository.findByRefMemberId(new RefMemberId(memberId));
        return userCoupons.stream()
                .map(uc -> {
                    CouponTemplateModel template = couponTemplateRepository.findById(uc.getRefCouponTemplateId().value())
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
                    return UserCouponInfo.from(uc, template.getExpiredAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateDiscount(Long userCouponId, Long memberId, BigDecimal originalAmount) {
        return couponService.calculateDiscount(userCouponId, memberId, originalAmount);
    }

    @Transactional
    public Long useUserCoupon(Long userCouponId) {
        return couponService.useUserCoupon(userCouponId);
    }

    @Transactional
    public void restoreUserCoupon(Long userCouponPkId) {
        couponService.restoreUserCouponByPkId(userCouponPkId);
    }
}
