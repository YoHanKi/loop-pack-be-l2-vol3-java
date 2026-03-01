package com.loopers.domain.coupon;

import com.loopers.domain.coupon.vo.CouponTemplateId;
import com.loopers.domain.coupon.vo.UserCouponId;
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
            CouponTemplateModel template = createActiveTemplate(10);
            when(couponTemplateRepository.findByCouponTemplateIdForUpdate(any())).thenReturn(Optional.of(template));
            when(userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(any(), any())).thenReturn(false);
            when(couponTemplateRepository.save(any())).thenReturn(template);
            UserCouponModel savedCoupon = mock(UserCouponModel.class);
            when(userCouponRepository.save(any())).thenReturn(savedCoupon);

            // when
            UserCouponModel result = couponService.issueUserCoupon(template.getCouponTemplateId().value(), memberId);

            // then
            assertThat(result).isEqualTo(savedCoupon);
            verify(couponTemplateRepository).save(template);
            verify(userCouponRepository).save(any(UserCouponModel.class));
        }

        @Test
        @DisplayName("만료된 템플릿 - 400 Bad Request")
        void issueUserCoupon_expired_throws400() {
            // given
            Long memberId = 1L;
            CouponTemplateModel expiredTemplate = CouponTemplateModel.create(
                    "만료쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                    ZonedDateTime.now().plusSeconds(1), 10
            );
            // 만료 상태 시뮬레이션을 위해 spy 사용
            CouponTemplateModel spy = spy(expiredTemplate);
            when(spy.isExpired()).thenReturn(true);
            when(couponTemplateRepository.findByCouponTemplateIdForUpdate(any())).thenReturn(Optional.of(spy));

            // when & then
            assertThatThrownBy(() -> couponService.issueUserCoupon(
                    expiredTemplate.getCouponTemplateId().value(), memberId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("수량 초과 - 409 Conflict")
        void issueUserCoupon_quantityExceeded_throws409() {
            // given
            Long memberId = 1L;
            CouponTemplateModel template = mock(CouponTemplateModel.class);
            when(template.isDeleted()).thenReturn(false);
            when(template.isExpired()).thenReturn(false);
            when(template.getIssuedQuantity()).thenReturn(10);
            when(template.getTotalQuantity()).thenReturn(10);
            when(couponTemplateRepository.findByCouponTemplateIdForUpdate(any())).thenReturn(Optional.of(template));

            // when & then
            assertThatThrownBy(() -> couponService.issueUserCoupon(
                    "00000000-0000-0000-0000-000000000001", memberId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
        }

        @Test
        @DisplayName("중복 발급 - 409 Conflict")
        void issueUserCoupon_duplicateIssue_throws409() {
            // given
            Long memberId = 1L;
            CouponTemplateModel template = mock(CouponTemplateModel.class);
            when(template.isDeleted()).thenReturn(false);
            when(template.isExpired()).thenReturn(false);
            when(template.getIssuedQuantity()).thenReturn(0);
            when(template.getTotalQuantity()).thenReturn(10);
            when(couponTemplateRepository.findByCouponTemplateIdForUpdate(any())).thenReturn(Optional.of(template));
            when(userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(any(), any())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> couponService.issueUserCoupon(
                    "00000000-0000-0000-0000-000000000001", memberId))
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
            BigDecimal originalAmount = BigDecimal.valueOf(50000);
            BigDecimal discountValue = BigDecimal.valueOf(5000);
            CouponTemplateModel template = setupBaseMocksForDiscount(null);
            when(template.getType()).thenReturn(CouponType.FIXED);
            when(template.getValue()).thenReturn(discountValue);

            // when
            BigDecimal discount = couponService.calculateDiscount("00000000-0000-0000-0000-000000000001", originalAmount);

            // then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("FIXED 쿠폰 - 주문금액 초과 시 주문금액까지 할인")
        void calculateDiscount_fixed_capped() {
            // given
            BigDecimal originalAmount = BigDecimal.valueOf(3000);
            BigDecimal discountValue = BigDecimal.valueOf(5000);
            CouponTemplateModel template = setupBaseMocksForDiscount(null);
            when(template.getType()).thenReturn(CouponType.FIXED);
            when(template.getValue()).thenReturn(discountValue);

            // when
            BigDecimal discount = couponService.calculateDiscount("00000000-0000-0000-0000-000000000001", originalAmount);

            // then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("RATE 쿠폰 - 정상 할인 계산")
        void calculateDiscount_rate_success() {
            // given
            BigDecimal originalAmount = BigDecimal.valueOf(10000);
            BigDecimal rateValue = BigDecimal.valueOf(10); // 10%
            CouponTemplateModel template = setupBaseMocksForDiscount(null);
            when(template.getType()).thenReturn(CouponType.RATE);
            when(template.getValue()).thenReturn(rateValue);

            // when
            BigDecimal discount = couponService.calculateDiscount("00000000-0000-0000-0000-000000000001", originalAmount);

            // then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @Test
        @DisplayName("최소 주문금액 미충족 - 400 Bad Request")
        void calculateDiscount_belowMinOrderAmount_throws400() {
            // given
            BigDecimal originalAmount = BigDecimal.valueOf(5000);
            BigDecimal minOrderAmount = BigDecimal.valueOf(10000);
            setupBaseMocksForDiscount(minOrderAmount);

            // when & then
            assertThatThrownBy(() -> couponService.calculateDiscount(
                    "00000000-0000-0000-0000-000000000001", originalAmount))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        // 공통 mock 설정: userCoupon + template 기본 스텁, template 반환
        private CouponTemplateModel setupBaseMocksForDiscount(BigDecimal minOrderAmount) {
            String userCouponIdValue = "00000000-0000-0000-0000-000000000001";
            UserCouponModel userCoupon = mock(UserCouponModel.class);
            when(userCoupon.isAvailable()).thenReturn(true);
            when(userCoupon.isExpired(any())).thenReturn(false);
            when(userCoupon.getRefCouponTemplateId()).thenReturn(1L);
            when(userCouponRepository.findByUserCouponId(new UserCouponId(userCouponIdValue)))
                    .thenReturn(Optional.of(userCoupon));

            CouponTemplateModel template = mock(CouponTemplateModel.class);
            when(template.getMinOrderAmount()).thenReturn(minOrderAmount);
            when(template.getExpiredAt()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(couponTemplateRepository.findByPkId(1L)).thenReturn(Optional.of(template));
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
            String userCouponIdValue = "00000000-0000-0000-0000-000000000001";
            UserCouponModel userCoupon = mock(UserCouponModel.class);
            when(userCoupon.getId()).thenReturn(1L);
            when(userCouponRepository.findByUserCouponId(new UserCouponId(userCouponIdValue)))
                    .thenReturn(Optional.of(userCoupon));
            when(userCouponRepository.useIfAvailable(1L)).thenReturn(1);

            // when
            Long pkId = couponService.useUserCoupon(userCouponIdValue);

            // then
            assertThat(pkId).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - rowsAffected == 0 → 409 Conflict")
        void useUserCoupon_alreadyUsed_throws409() {
            // given
            String userCouponIdValue = "00000000-0000-0000-0000-000000000001";
            UserCouponModel userCoupon = mock(UserCouponModel.class);
            when(userCoupon.getId()).thenReturn(1L);
            when(userCouponRepository.findByUserCouponId(new UserCouponId(userCouponIdValue)))
                    .thenReturn(Optional.of(userCoupon));
            when(userCouponRepository.useIfAvailable(1L)).thenReturn(0);

            // when & then
            assertThatThrownBy(() -> couponService.useUserCoupon(userCouponIdValue))
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

    private CouponTemplateModel createActiveTemplate(int totalQuantity) {
        return CouponTemplateModel.create(
                "테스트쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7), totalQuantity
        );
    }
}
