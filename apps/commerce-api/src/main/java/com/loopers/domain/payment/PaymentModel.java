package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.vo.RefOrderId;
import com.loopers.infrastructure.jpa.converter.RefOrderIdConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {@UniqueConstraint(name = "uq_payments_ref_order_id", columnNames = "ref_order_id")}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Convert(converter = RefOrderIdConverter.class)
    @Column(name = "ref_order_id", nullable = false)
    private RefOrderId refOrderId;

    @Column(name = "card_number", nullable = false, length = 16)
    private String cardNumber;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    private PaymentModel(Long orderId, String cardNumber, BigDecimal amount) {
        this.refOrderId = new RefOrderId(orderId);
        this.cardNumber = cardNumber;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static PaymentModel create(Long orderId, String cardNumber, BigDecimal amount) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호가 비어 있습니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        return new PaymentModel(orderId, cardNumber, amount);
    }

    public void requested(String pgTransactionId) {
        validateTransition(PaymentStatus.REQUESTED);
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.REQUESTED;
    }

    public void complete() {
        validateTransition(PaymentStatus.COMPLETED);
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail() {
        if (this.status == PaymentStatus.PENDING || this.status == PaymentStatus.REQUESTED) {
            this.status = PaymentStatus.FAILED;
            return;
        }
        throw new CoreException(ErrorType.BAD_REQUEST, "FAILED 전이 불가 상태: " + this.status);
    }

    private void validateTransition(PaymentStatus next) {
        boolean allowed = switch (this.status) {
            case PENDING -> next == PaymentStatus.REQUESTED;
            case REQUESTED -> next == PaymentStatus.COMPLETED;
            case COMPLETED, FAILED -> false;
        };
        if (!allowed) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 상태 전이 불가: " + this.status + " → " + next);
        }
    }
}
