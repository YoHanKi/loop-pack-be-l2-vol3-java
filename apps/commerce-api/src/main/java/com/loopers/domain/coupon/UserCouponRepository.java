package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel model);

    Optional<UserCouponModel> findById(Long id);

    List<UserCouponModel> findByRefMemberId(RefMemberId memberId);

    List<UserCouponModel> findByRefCouponTemplateId(RefCouponTemplateId templateId);

    boolean existsByRefMemberIdAndRefCouponTemplateId(RefMemberId memberId, RefCouponTemplateId templateId);

    int useIfAvailable(Long id);

    int restoreIfUsed(Long id);
}
