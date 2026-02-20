package com.loopers.domain.like;

import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findByRefMemberIdAndRefProductId(RefMemberId refMemberId, RefProductId refProductId);
    void delete(LikeModel like);
    Page<LikeModel> findByRefMemberId(RefMemberId refMemberId, Pageable pageable);
}
