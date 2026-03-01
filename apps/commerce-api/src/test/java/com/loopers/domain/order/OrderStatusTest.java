package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus Enum 테스트")
class OrderStatusTest {

    @Test
    @DisplayName("PENDING에서 CANCELED로 전이 가능")
    void canTransition_pendingToCanceled() {
        // given
        OrderStatus status = OrderStatus.PENDING;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    @DisplayName("CANCELED에서 CANCELED로 전이 가능 (멱등성)")
    void canTransition_canceledToCanceled() {
        // given
        OrderStatus status = OrderStatus.CANCELED;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    @DisplayName("CANCELED에서 PENDING으로 전이 불가")
    void cannotTransition_canceledToPending() {
        // given
        OrderStatus status = OrderStatus.CANCELED;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.PENDING);

        // then
        assertThat(canTransition).isFalse();
    }

    @Test
    @DisplayName("불가능한 상태 전이 시 예외 발생")
    void validateTransition_throwsException() {
        // given
        OrderStatus status = OrderStatus.CANCELED;

        // when & then
        assertThatThrownBy(() -> status.validateTransition(OrderStatus.PENDING))
                .isInstanceOf(CoreException.class);
    }
}
