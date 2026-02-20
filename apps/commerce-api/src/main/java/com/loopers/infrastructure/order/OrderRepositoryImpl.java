package com.loopers.infrastructure.order;

import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.vo.OrderId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;
    private final EntityManager entityManager;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<OrderModel> findByOrderId(OrderId orderId) {
        return orderJpaRepository.findByOrderId(orderId);
    }

    @Override
    public Page<OrderModel> findByRefMemberId(RefMemberId refMemberId, LocalDateTime startDateTime, LocalDateTime endDateTime, Pageable pageable) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM orders WHERE ref_member_id = :memberId");

        if (startDateTime != null) {
            sql.append(" AND created_at >= :startDateTime");
        }
        if (endDateTime != null) {
            sql.append(" AND created_at <= :endDateTime");
        }
        sql.append(" ORDER BY created_at DESC");

        var query = entityManager.createNativeQuery(sql.toString(), OrderModel.class)
                .setParameter("memberId", refMemberId.value());

        if (startDateTime != null) {
            query.setParameter("startDateTime", startDateTime);
        }
        if (endDateTime != null) {
            query.setParameter("endDateTime", endDateTime);
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<OrderModel> orders = query.getResultList();
        long total = countByRefMemberId(refMemberId, startDateTime, endDateTime);

        return new PageImpl<>(orders, pageable, total);
    }

    private long countByRefMemberId(RefMemberId refMemberId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM orders WHERE ref_member_id = :memberId");

        if (startDateTime != null) {
            sql.append(" AND created_at >= :startDateTime");
        }
        if (endDateTime != null) {
            sql.append(" AND created_at <= :endDateTime");
        }

        var query = entityManager.createNativeQuery(sql.toString())
                .setParameter("memberId", refMemberId.value());

        if (startDateTime != null) {
            query.setParameter("startDateTime", startDateTime);
        }
        if (endDateTime != null) {
            query.setParameter("endDateTime", endDateTime);
        }

        return ((Number) query.getSingleResult()).longValue();
    }
}
