package com.loopers.domain.coupon;

import com.loopers.domain.coupon.vo.UserCouponId;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel model);

    Optional<UserCouponModel> findByUserCouponId(UserCouponId id);

    List<UserCouponModel> findByRefMemberId(Long memberId);

    List<UserCouponModel> findByRefCouponTemplateId(Long templateId);

    boolean existsByRefMemberIdAndRefCouponTemplateId(Long memberId, Long templateId);

    int useIfAvailable(Long id);

    int restoreIfUsed(Long id);
}
