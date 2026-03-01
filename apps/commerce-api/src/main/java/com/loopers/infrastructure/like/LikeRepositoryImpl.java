package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

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
        return likeJpaRepository.findActiveByRefMemberId(refMemberId.value(), pageable);
    }
}
