package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    Optional<LikeModel> findByRefMemberIdAndRefProductId(RefMemberId refMemberId, RefProductId refProductId);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE likes SET deleted_at = NULL, updated_at = NOW() WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    int restoreIfDeleted(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE likes SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id AND deleted_at IS NULL",
            nativeQuery = true
    )
    int softDeleteIfActive(@Param("id") Long id);

    @Query(
            value = "SELECT l.* FROM likes l JOIN products p ON l.ref_product_id = p.id " +
                    "WHERE l.ref_member_id = :memberId AND l.deleted_at IS NULL AND p.deleted_at IS NULL " +
                    "ORDER BY l.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM likes l JOIN products p ON l.ref_product_id = p.id " +
                    "WHERE l.ref_member_id = :memberId AND l.deleted_at IS NULL AND p.deleted_at IS NULL",
            nativeQuery = true
    )
    Page<LikeModel> findActiveByRefMemberId(@Param("memberId") Long memberId, Pageable pageable);
}
