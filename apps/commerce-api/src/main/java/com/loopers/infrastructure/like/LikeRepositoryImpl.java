package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import lombok.RequiredArgsConstructor;
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
}
