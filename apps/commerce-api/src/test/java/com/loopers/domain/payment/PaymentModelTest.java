package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentModel Entity")
class PaymentModelTest {

    private static final Long ORDER_ID = 1L;
    private static final String CARD_NUMBER = "1234567890123456";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @Test
        @DisplayName("정상 입력으로 생성 시 PENDING 상태로 생성된다")
        void create_withValidInput_returnsPendingPayment() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);

            assertThat(payment.getRefOrderId().value()).isEqualTo(ORDER_ID);
            assertThat(payment.getCardNumber()).isEqualTo(CARD_NUMBER);
            assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPgTransactionId()).isNull();
        }

        @Test
        @DisplayName("카드 번호가 null이면 예외가 발생한다")
        void create_withNullCardNumber_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, null, AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("카드 번호가 공백이면 예외가 발생한다")
        void create_withBlankCardNumber_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, "   ", AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("결제 금액이 0이면 예외가 발생한다")
        void create_withZeroAmount_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, CARD_NUMBER, BigDecimal.ZERO))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("결제 금액이 음수이면 예외가 발생한다")
        void create_withNegativeAmount_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, CARD_NUMBER, new BigDecimal("-1")))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 요청 상태로 전이할 때,")
    @Nested
    class Requested {

        @Test
        @DisplayName("PENDING 상태에서 requested() 호출 시 REQUESTED 상태가 된다")
        void requested_fromPending_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);
            String pgTransactionId = "pg-txn-001";

            payment.requested(pgTransactionId);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getPgTransactionId()).isEqualTo(pgTransactionId);
        }

        @Test
        @DisplayName("REQUESTED 상태에서 requested() 호출 시 예외가 발생한다")
        void requested_fromRequested_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);
            payment.requested("pg-txn-001");

            assertThatThrownBy(() -> payment.requested("pg-txn-002"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 완료 상태로 전이할 때,")
    @Nested
    class Complete {

        @Test
        @DisplayName("REQUESTED 상태에서 complete() 호출 시 COMPLETED 상태가 된다")
        void complete_fromRequested_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);
            payment.requested("pg-txn-001");

            payment.complete();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("PENDING 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromPending_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);

            assertThatThrownBy(payment::complete)
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromCompleted_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);
            payment.requested("pg-txn-001");
            payment.complete();

            assertThatThrownBy(payment::complete)
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 실패 상태로 전이할 때,")
    @Nested
    class Fail {

        @Test
        @DisplayName("PENDING 상태에서 fail() 호출 시 FAILED 상태가 된다")
        void fail_fromPending_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);

            payment.fail();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("REQUESTED 상태에서 fail() 호출 시 FAILED 상태가 된다")
        void fail_fromRequested_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);
            payment.requested("pg-txn-001");

            payment.fail();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 fail() 호출 시 예외가 발생한다")
        void fail_fromCompleted_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, CARD_NUMBER, AMOUNT);
            payment.requested("pg-txn-001");
            payment.complete();

            assertThatThrownBy(payment::fail)
                    .isInstanceOf(CoreException.class);
        }
    }
}
