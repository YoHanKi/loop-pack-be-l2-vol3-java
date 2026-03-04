package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel model);

    Optional<UserCouponModel> findById(Long id);

    List<UserCouponModel> findByRefMemberId(Long memberId);

    List<UserCouponModel> findByRefCouponTemplateId(Long templateId);

    boolean existsByRefMemberIdAndRefCouponTemplateId(Long memberId, Long templateId);

    int useIfAvailable(Long id);

    int restoreIfUsed(Long id);
}
