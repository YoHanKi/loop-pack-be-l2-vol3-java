package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    Optional<LikeModel> findByRefMemberIdAndRefProductId(RefMemberId refMemberId, RefProductId refProductId);
}
