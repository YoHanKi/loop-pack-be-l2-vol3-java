package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.vo.CouponTemplateId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public Optional<CouponTemplateModel> findByCouponTemplateId(CouponTemplateId id) {
        return couponTemplateJpaRepository.findByCouponTemplateId(id);
    }

    @Override
    public Optional<CouponTemplateModel> findByCouponTemplateIdForUpdate(CouponTemplateId id) {
        return couponTemplateJpaRepository.findByCouponTemplateIdForUpdate(id);
    }

    @Override
    public Optional<CouponTemplateModel> findByPkId(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public Page<CouponTemplateModel> findAll(Pageable pageable) {
        return couponTemplateJpaRepository.findAll(pageable);
    }

    @Override
    public CouponTemplateModel save(CouponTemplateModel model) {
        return couponTemplateJpaRepository.save(model);
    }
}
