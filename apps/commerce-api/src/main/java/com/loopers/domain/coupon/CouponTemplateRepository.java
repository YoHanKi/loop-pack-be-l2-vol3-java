package com.loopers.domain.coupon;

import com.loopers.domain.coupon.vo.CouponTemplateId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponTemplateRepository {

    Optional<CouponTemplateModel> findByCouponTemplateId(CouponTemplateId id);

    Optional<CouponTemplateModel> findByCouponTemplateIdForUpdate(CouponTemplateId id);

    Optional<CouponTemplateModel> findByPkId(Long id);

    Page<CouponTemplateModel> findAll(Pageable pageable);

    CouponTemplateModel save(CouponTemplateModel model);
}
