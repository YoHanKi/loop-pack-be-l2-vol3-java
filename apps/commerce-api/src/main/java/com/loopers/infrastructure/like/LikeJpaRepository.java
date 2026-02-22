package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    Optional<LikeModel> findByRefMemberIdAndRefProductId(RefMemberId refMemberId, RefProductId refProductId);

    @Query(
            value = "SELECT l.* FROM likes l JOIN products p ON l.ref_product_id = p.id " +
                    "WHERE l.ref_member_id = :memberId AND p.deleted_at IS NULL " +
                    "ORDER BY l.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM likes l JOIN products p ON l.ref_product_id = p.id " +
                    "WHERE l.ref_member_id = :memberId AND p.deleted_at IS NULL",
            nativeQuery = true
    )
    Page<LikeModel> findActiveByRefMemberId(@Param("memberId") Long memberId, Pageable pageable);
}
