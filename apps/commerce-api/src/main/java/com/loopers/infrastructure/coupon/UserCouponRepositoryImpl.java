package com.loopers.infrastructure.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel model) {
        return userCouponJpaRepository.save(model);
    }

    @Override
    public Optional<UserCouponModel> findById(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public List<UserCouponModel> findByRefMemberId(RefMemberId memberId) {
        return userCouponJpaRepository.findByRefMemberId(memberId);
    }

    @Override
    public List<UserCouponModel> findByRefCouponTemplateId(RefCouponTemplateId templateId) {
        return userCouponJpaRepository.findByRefCouponTemplateId(templateId);
    }

    @Override
    public boolean existsByRefMemberIdAndRefCouponTemplateId(RefMemberId memberId, RefCouponTemplateId templateId) {
        return userCouponJpaRepository.existsByRefMemberIdAndRefCouponTemplateId(memberId, templateId);
    }

    @Override
    public int useIfAvailable(Long id) {
        return userCouponJpaRepository.useIfAvailable(id);
    }

    @Override
    public int restoreIfUsed(Long id) {
        return userCouponJpaRepository.restoreIfUsed(id);
    }
}
