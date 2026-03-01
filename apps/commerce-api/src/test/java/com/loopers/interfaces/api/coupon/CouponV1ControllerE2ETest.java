package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApp;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("CouponV1Controller E2E 테스트")
class CouponV1ControllerE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private CouponApp couponApp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private CouponTemplateModel template;

    @BeforeEach
    void setUp() {
        template = CouponTemplateModel.create(
                "테스트쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7), 5
        );
        couponTemplateRepository.save(template);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/coupons/{couponId}/issue - 쿠폰 발급")
    class IssueCoupon {

        @Test
        @DisplayName("쿠폰 발급 성공 - 201 Created")
        void issueCoupon_success_returns201() {
            // given
            Long memberId = 1L;
            String url = "/api/v1/coupons/" + template.getCouponTemplateId().value() + "/issue";
            Map<String, Long> body = Map.of("memberId", memberId);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(url, body, ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data()).isNotNull();
        }

        @Test
        @DisplayName("중복 발급 실패 - 409 Conflict")
        void issueCoupon_duplicate_returns409() {
            // given
            Long memberId = 1L;
            String url = "/api/v1/coupons/" + template.getCouponTemplateId().value() + "/issue";
            Map<String, Long> body = Map.of("memberId", memberId);

            restTemplate.postForEntity(url, body, ApiResponse.class);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(url, body, ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 - 404 Not Found")
        void issueCoupon_notFound_returns404() {
            // given
            String url = "/api/v1/coupons/00000000-0000-0000-0000-000000000001/issue";
            Map<String, Long> body = Map.of("memberId", 1L);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(url, body, ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/coupons - 내 쿠폰 목록 조회")
    class GetMyUserCoupons {

        @Test
        @DisplayName("쿠폰 발급 후 목록 조회 - 200 OK, 1건 반환")
        void getMyUserCoupons_afterIssue_returns200WithCoupons() {
            // given
            Long memberId = 1L;
            couponApp.issueUserCoupon(template.getCouponTemplateId().value(), memberId);

            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = restTemplate.exchange(
                    "/api/v1/users/me/coupons?memberId=" + memberId,
                    HttpMethod.GET, null, responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).hasSize(1),
                    () -> assertThat(response.getBody().data().get(0).refMemberId()).isEqualTo(memberId)
            );
        }

        @Test
        @DisplayName("쿠폰이 없는 회원 - 200 OK, 빈 목록")
        void getMyUserCoupons_noIssue_returnsEmptyList() {
            // given
            Long memberId = 99L;
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = restTemplate.exchange(
                    "/api/v1/users/me/coupons?memberId=" + memberId,
                    HttpMethod.GET, null, responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }
}
