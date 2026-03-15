package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PgResult VO 테스트")
class PgResultTest {

    @Test
    @DisplayName("unavailable() 팩토리 메서드는 pgTransactionId가 null이고 UNAVAILABLE 상태를 반환한다")
    void unavailable_returnsCorrectResult() {
        PgResult result = PgResult.unavailable();

        assertThat(result.pgTransactionId()).isNull();
        assertThat(result.status()).isEqualTo(PgStatus.UNAVAILABLE);
        assertThat(result.isUnavailable()).isTrue();
    }

    @Test
    @DisplayName("PROCESSING 상태일 때 isAccepted()는 true를 반환한다")
    void isAccepted_whenProcessing_returnsTrue() {
        PgResult result = new PgResult("txId", PgStatus.PROCESSING);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.isUnavailable()).isFalse();
    }

    @Test
    @DisplayName("COMPLETED 상태일 때 isAccepted()는 false를 반환한다")
    void isAccepted_whenCompleted_returnsFalse() {
        PgResult result = new PgResult("txId", PgStatus.COMPLETED);

        assertThat(result.isAccepted()).isFalse();
    }
}
