package com.loopers.domain.like;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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

        return likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                .map(existingLike -> {
                    if (existingLike.getDeletedAt() != null) {
                        // soft-delete된 경우 조건부 복원: 이 스레드가 실제로 복원한 경우에만 count 증가
                        int restored = likeRepository.restoreIfDeleted(existingLike.getId());
                        if (restored > 0) {
                            existingLike.restore(); // 인메모리 상태 동기화
                            productRepository.incrementLikeCount(product.getId());
                        }
                    }
                    return existingLike;
                })
                .orElseGet(() -> {
                    LikeModel like = LikeModel.create(memberId, product.getId());
                    LikeModel saved = likeRepository.save(like);
                    productRepository.incrementLikeCount(product.getId());
                    return saved;
                });
    }

    public void removeLike(Long memberId, String productId) {
        // 상품 존재 확인
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        RefMemberId refMemberId = new RefMemberId(memberId);
        RefProductId refProductId = new RefProductId(product.getId());

        // 조건부 소프트 삭제: 이 스레드가 실제로 삭제한 경우에만 count 감소 (멱등성)
        likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                .ifPresent(like -> {
                    if (like.getDeletedAt() == null) {
                        int deleted = likeRepository.softDeleteIfActive(like.getId());
                        if (deleted > 0) {
                            productRepository.decrementLikeCount(product.getId());
                        }
                    }
                });
    }
}
