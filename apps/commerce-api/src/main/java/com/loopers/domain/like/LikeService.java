package com.loopers.domain.like;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    public LikeModel addLike(Long memberId, String productId) {
        // 상품 존재 확인
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        RefMemberId refMemberId = new RefMemberId(memberId);
        RefProductId refProductId = new RefProductId(product.getId());

        // 이미 좋아요가 있는지 확인 (멱등성)
        return likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                .orElseGet(() -> {
                    try {
                        LikeModel like = LikeModel.create(memberId, product.getId());
                        return likeRepository.save(like);
                    } catch (DataIntegrityViolationException e) {
                        // 동시성 이슈로 UNIQUE 제약 위반 시 다시 조회
                        return likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                                .orElseThrow(() -> new CoreException(ErrorType.CONFLICT, "좋아요 추가 중 오류가 발생했습니다."));
                    }
                });
    }

    public void removeLike(Long memberId, String productId) {
        // 상품 존재 확인
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        RefMemberId refMemberId = new RefMemberId(memberId);
        RefProductId refProductId = new RefProductId(product.getId());

        // 좋아요가 있으면 삭제 (멱등성 - 없어도 예외 발생 안함)
        likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                .ifPresent(likeRepository::delete);
    }
}
