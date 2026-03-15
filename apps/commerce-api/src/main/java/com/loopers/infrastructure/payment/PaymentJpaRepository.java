package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.vo.RefOrderId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByPgTransactionId(String pgTransactionId);

    Optional<PaymentModel> findByRefOrderId(RefOrderId refOrderId);
}
