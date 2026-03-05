package com.loopers.infrastructure.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    List<UserCouponModel> findByRefMemberId(RefMemberId memberId);

    List<UserCouponModel> findByRefCouponTemplateId(RefCouponTemplateId templateId);

    boolean existsByRefMemberIdAndRefCouponTemplateId(RefMemberId memberId, RefCouponTemplateId templateId);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE user_coupons SET status = 'USED', version = version + 1 WHERE id = :id AND status = 'AVAILABLE'",
            nativeQuery = true
    )
    int useIfAvailable(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE user_coupons SET status = 'AVAILABLE', version = version + 1 WHERE id = :id AND status = 'USED'",
            nativeQuery = true
    )
    int restoreIfUsed(@Param("id") Long id);
}
