package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    @Nested
    @DisplayName("쿠폰 발급 (issueUserCoupon)")
    class IssueUserCoupon {

        @Test
        @DisplayName("정상 발급 성공")
        void issueUserCoupon_success() {
            // given
            Long memberId = 1L;
            Long couponTemplateId = 42L;
            CouponTemplateModel template = createActiveTemplate();
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.of(template));
            when(userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(any(), any())).thenReturn(false);
            UserCouponModel savedCoupon = mock(UserCouponModel.class);
            when(userCouponRepository.save(any())).thenReturn(savedCoupon);

            // when
            UserCouponModel result = couponService.issueUserCoupon(couponTemplateId, memberId);

            // then
            assertThat(result).isEqualTo(savedCoupon);
            verify(userCouponRepository).save(any(UserCouponModel.class));
        }

        @Test
        @DisplayName("만료된 템플릿 - 400 Bad Request")
        void issueUserCoupon_expired_throws400() {
            // given
            Long memberId = 1L;
            Long couponTemplateId = 42L;
            CouponTemplateModel expiredTemplate = CouponTemplateModel.create(
                    "만료쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                    ZonedDateTime.now().plusSeconds(1)
            );
            CouponTemplateModel spy = spy(expiredTemplate);
            when(spy.isExpired()).thenReturn(true);
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.of(spy));

            // when & then
            assertThatThrownBy(() -> couponService.issueUserCoupon(couponTemplateId, memberId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("중복 발급 - 409 Conflict")
        void issueUserCoupon_duplicateIssue_throws409() {
            // given
            Long memberId = 1L;
            Long couponTemplateId = 42L;
            CouponTemplateModel template = mock(CouponTemplateModel.class);
            when(template.isDeleted()).thenReturn(false);
            when(template.isExpired()).thenReturn(false);
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.of(template));
            when(userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(any(), any())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> couponService.issueUserCoupon(couponTemplateId, memberId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("할인 계산 (calculateDiscount)")
    class CalculateDiscount {

        @Test
        @DisplayName("FIXED 쿠폰 - 정상 할인")
        void calculateDiscount_fixed_success() {
            // given
            Long memberId = 1L;
            BigDecimal originalAmount = BigDecimal.valueOf(50000);
            BigDecimal discountValue = BigDecimal.valueOf(5000);
            CouponTemplateModel template = setupBaseMocksForDiscount(memberId, null);
            when(template.getType()).thenReturn(CouponType.FIXED);
            when(template.getValue()).thenReturn(discountValue);

            // when
            BigDecimal discount = couponService.calculateDiscount(1L, memberId, originalAmount);

            // then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("FIXED 쿠폰 - 주문금액 초과 시 주문금액까지 할인")
        void calculateDiscount_fixed_capped() {
            // given
            Long memberId = 1L;
            BigDecimal originalAmount = BigDecimal.valueOf(3000);
            BigDecimal discountValue = BigDecimal.valueOf(5000);
            CouponTemplateModel template = setupBaseMocksForDiscount(memberId, null);
            when(template.getType()).thenReturn(CouponType.FIXED);
            when(template.getValue()).thenReturn(discountValue);

            // when
            BigDecimal discount = couponService.calculateDiscount(1L, memberId, originalAmount);

            // then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("RATE 쿠폰 - 정상 할인 계산")
        void calculateDiscount_rate_success() {
            // given
            Long memberId = 1L;
            BigDecimal originalAmount = BigDecimal.valueOf(10000);
            BigDecimal rateValue = BigDecimal.valueOf(10); // 10%
            CouponTemplateModel template = setupBaseMocksForDiscount(memberId, null);
            when(template.getType()).thenReturn(CouponType.RATE);
            when(template.getValue()).thenReturn(rateValue);

            // when
            BigDecimal discount = couponService.calculateDiscount(1L, memberId, originalAmount);

            // then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @Test
        @DisplayName("타 유저 쿠폰 사용 시도 - 403 Forbidden")
        void calculateDiscount_otherMemberCoupon_throws403() {
            // given
            Long ownerMemberId = 1L;
            Long attackerMemberId = 2L;
            Long userCouponId = 1L;
            UserCouponModel userCoupon = mock(UserCouponModel.class);
            when(userCoupon.getRefMemberId()).thenReturn(ownerMemberId);
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.calculateDiscount(
                    userCouponId, attackerMemberId, BigDecimal.valueOf(10000)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.FORBIDDEN);
        }

        @Test
        @DisplayName("최소 주문금액 미충족 - 400 Bad Request")
        void calculateDiscount_belowMinOrderAmount_throws400() {
            // given
            Long memberId = 1L;
            BigDecimal originalAmount = BigDecimal.valueOf(5000);
            BigDecimal minOrderAmount = BigDecimal.valueOf(10000);
            setupBaseMocksForDiscount(memberId, minOrderAmount);

            // when & then
            assertThatThrownBy(() -> couponService.calculateDiscount(1L, memberId, originalAmount))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        private CouponTemplateModel setupBaseMocksForDiscount(Long memberId, BigDecimal minOrderAmount) {
            Long userCouponId = 1L;
            UserCouponModel userCoupon = mock(UserCouponModel.class);
            when(userCoupon.getRefMemberId()).thenReturn(memberId);
            when(userCoupon.isAvailable()).thenReturn(true);
            when(userCoupon.isExpired(any())).thenReturn(false);
            when(userCoupon.getRefCouponTemplateId()).thenReturn(1L);
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

            CouponTemplateModel template = mock(CouponTemplateModel.class);
            when(template.getMinOrderAmount()).thenReturn(minOrderAmount);
            when(template.getExpiredAt()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
            return template;
        }
    }

    @Nested
    @DisplayName("쿠폰 사용 (useUserCoupon)")
    class UseUserCoupon {

        @Test
        @DisplayName("성공 - rowsAffected == 1")
        void useUserCoupon_success() {
            // given
            Long userCouponId = 1L;
            when(userCouponRepository.useIfAvailable(userCouponId)).thenReturn(1);

            // when
            Long pkId = couponService.useUserCoupon(userCouponId);

            // then
            assertThat(pkId).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - rowsAffected == 0 → 409 Conflict")
        void useUserCoupon_alreadyUsed_throws409() {
            // given
            Long userCouponId = 1L;
            when(userCouponRepository.useIfAvailable(userCouponId)).thenReturn(0);

            // when & then
            assertThatThrownBy(() -> couponService.useUserCoupon(userCouponId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("쿠폰 복원 (restoreUserCouponByPkId)")
    class RestoreUserCoupon {

        @Test
        @DisplayName("idempotent - rowsAffected == 0도 성공 처리")
        void restoreUserCoupon_idempotent() {
            // given
            Long pkId = 1L;
            when(userCouponRepository.restoreIfUsed(pkId)).thenReturn(0);

            // when & then (no exception)
            couponService.restoreUserCouponByPkId(pkId);
            verify(userCouponRepository).restoreIfUsed(pkId);
        }
    }

    private CouponTemplateModel createActiveTemplate() {
        return CouponTemplateModel.create(
                "테스트쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7)
        );
    }
}
