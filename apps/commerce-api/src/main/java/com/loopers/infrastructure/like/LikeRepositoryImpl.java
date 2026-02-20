package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;
    private final EntityManager entityManager;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<LikeModel> findByRefMemberIdAndRefProductId(RefMemberId refMemberId, RefProductId refProductId) {
        return likeJpaRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId);
    }

    @Override
    public void delete(LikeModel like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public Page<LikeModel> findByRefMemberId(RefMemberId refMemberId, Pageable pageable) {
        String sql = "SELECT l.* FROM likes l " +
                "JOIN products p ON l.ref_product_id = p.id " +
                "WHERE l.ref_member_id = :memberId " +
                "AND p.deleted_at IS NULL " +
                "ORDER BY l.created_at DESC";

        List<LikeModel> likes = entityManager.createNativeQuery(sql, LikeModel.class)
                .setParameter("memberId", refMemberId.value())
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        long total = countByRefMemberId(refMemberId);

        return new PageImpl<>(likes, pageable, total);
    }

    private long countByRefMemberId(RefMemberId refMemberId) {
        String sql = "SELECT COUNT(*) FROM likes l " +
                "JOIN products p ON l.ref_product_id = p.id " +
                "WHERE l.ref_member_id = :memberId " +
                "AND p.deleted_at IS NULL";

        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("memberId", refMemberId.value())
                .getSingleResult()).longValue();
    }
}
