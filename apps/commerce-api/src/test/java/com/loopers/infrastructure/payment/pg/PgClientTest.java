package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "pg.simulator.url=http://localhost:8089",
        "spring.cloud.openfeign.client.config.pg-client.read-timeout=500"
    }
)
@AutoConfigureWireMock(port = 8089)
@DisplayName("PgClient WireMock 통합 테스트")
class PgClientTest {

    @Autowired
    private PgClient pgClient;

    private static final String MEMBER_ID = "1";
    private static final String TRANSACTION_KEY = "txKey-test-001";
    private static final String ORDER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Nested
    @DisplayName("결제 요청 (POST /api/v1/payments)")
    class RequestPayment {

        @Test
        @DisplayName("PG가 200 응답 시 PgTransactionResponse를 반환한다")
        void success() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"transactionKey": "%s", "status": "PENDING"}
                        """.formatted(TRANSACTION_KEY))));

            PgPaymentRequest request = new PgPaymentRequest(
                ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L,
                "http://localhost:8080/api/v1/payments/callback");

            PgTransactionResponse response = pgClient.requestPayment(MEMBER_ID, request);

            assertThat(response.transactionKey()).isEqualTo(TRANSACTION_KEY);
            assertThat(response.status()).isEqualTo(PgStatus.PENDING);
        }

        @Test
        @DisplayName("PG가 400 응답 시 BAD_REQUEST CoreException이 발생한다")
        void badRequest() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("잘못된 요청입니다.")));

            PgPaymentRequest request = new PgPaymentRequest(
                ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L,
                "http://localhost:8080/api/v1/payments/callback");

            assertThatThrownBy(() -> pgClient.requestPayment(MEMBER_ID, request))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("PG가 500 응답 시 INTERNAL_ERROR CoreException이 발생한다")
        void internalServerError() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(500)
                    .withBody("현재 서버가 불안정합니다.")));

            PgPaymentRequest request = new PgPaymentRequest(
                ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L,
                "http://localhost:8080/api/v1/payments/callback");

            assertThatThrownBy(() -> pgClient.requestPayment(MEMBER_ID, request))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                    .isEqualTo(ErrorType.INTERNAL_ERROR));
        }

        @Test
        @DisplayName("PG가 503 응답 시 INTERNAL_ERROR CoreException이 발생한다")
        void serviceUnavailable() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(503)
                    .withBody("서비스 점검 중입니다.")));

            PgPaymentRequest request = new PgPaymentRequest(
                ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L,
                "http://localhost:8080/api/v1/payments/callback");

            assertThatThrownBy(() -> pgClient.requestPayment(MEMBER_ID, request))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                    .isEqualTo(ErrorType.INTERNAL_ERROR));
        }

        @Test
        @DisplayName("PG 응답이 readTimeout(500ms)을 초과하면 IOException이 발생한다")
        void readTimeout() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withFixedDelay(1000)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

            PgPaymentRequest request = new PgPaymentRequest(
                ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L,
                "http://localhost:8080/api/v1/payments/callback");

            assertThatThrownBy(() -> pgClient.requestPayment(MEMBER_ID, request))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Read timed out");
        }
    }

    @Nested
    @DisplayName("결제 상태 조회 (GET /api/v1/payments/{transactionKey})")
    class GetPaymentStatus {

        @Test
        @DisplayName("transactionKey로 조회 시 PgTransactionDetailResponse를 반환한다")
        void success() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "transactionKey": "%s",
                          "orderId": "%s",
                          "cardType": "SAMSUNG",
                          "cardNo": "1234-5678-9012-3456",
                          "amount": 10000,
                          "status": "SUCCESS",
                          "reason": "정상 승인되었습니다."
                        }
                        """.formatted(TRANSACTION_KEY, ORDER_ID))));

            PgTransactionDetailResponse response = pgClient.getPaymentStatus(MEMBER_ID, TRANSACTION_KEY);

            assertThat(response.transactionKey()).isEqualTo(TRANSACTION_KEY);
            assertThat(response.status()).isEqualTo(PgStatus.SUCCESS);
            assertThat(response.cardType()).isEqualTo(CardType.SAMSUNG);
        }
    }

    @Nested
    @DisplayName("주문 ID로 결제 조회 (GET /api/v1/payments?orderId=)")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("orderId로 조회 시 PgOrderResponse를 반환한다")
        void success() {
            stubFor(get(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "orderId": "%s",
                          "transactions": [
                            {"transactionKey": "%s", "status": "PENDING"}
                          ]
                        }
                        """.formatted(ORDER_ID, TRANSACTION_KEY))));

            PgOrderResponse response = pgClient.getPaymentByOrderId(MEMBER_ID, ORDER_ID);

            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(response.transactions()).hasSize(1);
            assertThat(response.transactions().get(0).transactionKey()).isEqualTo(TRANSACTION_KEY);
        }
    }
}
