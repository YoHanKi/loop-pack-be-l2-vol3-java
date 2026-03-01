package com.loopers.infrastructure.order;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.vo.OrderId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    Optional<OrderModel> findByOrderId(OrderId orderId);

    @Query("SELECT o FROM OrderModel o WHERE o.refMemberId = :refMemberId " +
            "AND (:startDateTime IS NULL OR o.createdAt >= :startDateTime) " +
            "AND (:endDateTime IS NULL OR o.createdAt <= :endDateTime) " +
            "ORDER BY o.createdAt DESC")
    Page<OrderModel> findByRefMemberIdWithDateFilter(
            @Param("refMemberId") RefMemberId refMemberId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable
    );
}
