package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.vo.CouponTemplateId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, Long> {

    Optional<CouponTemplateModel> findByCouponTemplateId(CouponTemplateId id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponTemplateModel c WHERE c.couponTemplateId = :id")
    Optional<CouponTemplateModel> findByCouponTemplateIdForUpdate(@Param("id") CouponTemplateId id);
}
