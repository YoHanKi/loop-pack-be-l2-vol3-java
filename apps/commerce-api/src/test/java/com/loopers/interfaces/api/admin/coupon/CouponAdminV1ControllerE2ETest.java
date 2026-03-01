package com.loopers.interfaces.api.admin.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("CouponAdminV1Controller E2E 테스트")
class CouponAdminV1ControllerE2ETest {

    private static final String ADMIN_URL = "/api-admin/v1/coupons";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api-admin/v1/coupons - 쿠폰 템플릿 생성")
    class CreateTemplate {

        @Test
        @DisplayName("쿠폰 생성 성공 - 201 Created")
        void createTemplate_success_returns201() {
            // given
            HttpHeaders headers = adminHeaders();
            Map<String, Object> body = Map.of(
                    "name", "신규회원 할인쿠폰",
                    "type", "FIXED",
                    "value", 5000,
                    "totalQuantity", 100,
                    "expiredAt", ZonedDateTime.now().plusDays(30).toString()
            );

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_URL, HttpMethod.POST, new HttpEntity<>(body, headers), ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data()).isNotNull();
        }

        @Test
        @DisplayName("어드민 인증 실패 - 403 Forbidden")
        void createTemplate_noAuth_returns403() {
            // given
            Map<String, Object> body = Map.of(
                    "name", "테스트", "type", "FIXED", "value", 1000,
                    "totalQuantity", 10, "expiredAt", ZonedDateTime.now().plusDays(1).toString());

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_URL, HttpMethod.POST, new HttpEntity<>(body), ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons/{couponId} - 쿠폰 상세 조회")
    class GetTemplate {

        @Test
        @DisplayName("쿠폰 조회 성공 - 200 OK")
        void getTemplate_success_returns200() {
            // given
            CouponTemplateModel template = createAndSaveTemplate("테스트쿠폰");
            HttpHeaders headers = adminHeaders();

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_URL + "/" + template.getCouponTemplateId().value(),
                    HttpMethod.GET, new HttpEntity<>(headers), ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 - 404 Not Found")
        void getTemplate_notFound_returns404() {
            // given
            HttpHeaders headers = adminHeaders();

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_URL + "/00000000-0000-0000-0000-000000000099",
                    HttpMethod.GET, new HttpEntity<>(headers), ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/coupons/{couponId} - 쿠폰 수정")
    class UpdateTemplate {

        @Test
        @DisplayName("쿠폰 수정 성공 - 200 OK")
        void updateTemplate_success_returns200() {
            // given
            CouponTemplateModel template = createAndSaveTemplate("기존쿠폰");
            HttpHeaders headers = adminHeaders();
            Map<String, Object> body = Map.of(
                    "name", "수정된쿠폰",
                    "value", 2000,
                    "totalQuantity", 50,
                    "expiredAt", ZonedDateTime.now().plusDays(14).toString()
            );

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_URL + "/" + template.getCouponTemplateId().value(),
                    HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/coupons/{couponId} - 쿠폰 삭제")
    class DeleteTemplate {

        @Test
        @DisplayName("쿠폰 삭제 성공 - 204 No Content")
        void deleteTemplate_success_returns204() {
            // given
            CouponTemplateModel template = createAndSaveTemplate("삭제할쿠폰");
            HttpHeaders headers = adminHeaders();

            // when
            ResponseEntity<Void> response = restTemplate.exchange(
                    ADMIN_URL + "/" + template.getCouponTemplateId().value(),
                    HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues - 발급 내역 조회")
    class GetIssuedCoupons {

        @Test
        @DisplayName("발급 내역 조회 성공 - 200 OK")
        void getIssuedCoupons_success_returns200() {
            // given
            CouponTemplateModel template = createAndSaveTemplate("발급내역쿠폰");
            HttpHeaders headers = adminHeaders();

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_URL + "/" + template.getCouponTemplateId().value() + "/issues",
                    HttpMethod.GET, new HttpEntity<>(headers), ApiResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private CouponTemplateModel createAndSaveTemplate(String name) {
        CouponTemplateModel template = CouponTemplateModel.create(
                name, CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(7), 10
        );
        return couponTemplateRepository.save(template);
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        return headers;
    }
}
