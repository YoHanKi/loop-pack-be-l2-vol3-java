package com.loopers.domain.like;

import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LikeService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요를 추가할 때,")
    @Nested
    class AddLike {

        @Test
        @DisplayName("유효한 상품에 좋아요 추가 성공")
        void addLike_success() {
            // given
            Long memberId = 1L;
            String productId = "prod1";
            ProductModel mockProduct = mock(ProductModel.class);
            when(mockProduct.getId()).thenReturn(100L);

            LikeModel mockLike = LikeModel.create(memberId, 100L);

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.of(mockProduct));
            when(likeRepository.findByRefMemberIdAndRefProductId(any(RefMemberId.class), any(RefProductId.class)))
                    .thenReturn(Optional.empty());
            when(likeRepository.save(any(LikeModel.class))).thenReturn(mockLike);

            // when
            LikeModel result = likeService.addLike(memberId, productId);

            // then
            assertThat(result).isNotNull();
            verify(productRepository, times(1)).findByProductId(any(ProductId.class));
            verify(likeRepository, times(1)).findByRefMemberIdAndRefProductId(any(RefMemberId.class), any(RefProductId.class));
            verify(likeRepository, times(1)).save(any(LikeModel.class));
        }

        @Test
        @DisplayName("이미 좋아요가 있으면 기존 좋아요 반환 (멱등성)")
        void addLike_alreadyExists_returnsExisting() {
            // given
            Long memberId = 1L;
            String productId = "prod1";
            ProductModel mockProduct = mock(ProductModel.class);
            when(mockProduct.getId()).thenReturn(100L);
            LikeModel existingLike = LikeModel.create(memberId, 100L);

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.of(mockProduct));
            when(likeRepository.findByRefMemberIdAndRefProductId(any(RefMemberId.class), any(RefProductId.class)))
                    .thenReturn(Optional.of(existingLike));

            // when
            LikeModel result = likeService.addLike(memberId, productId);

            // then
            assertThat(result).isEqualTo(existingLike);
            verify(likeRepository, never()).save(any(LikeModel.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 추가 시 예외 발생")
        void addLike_productNotFound_throwsException() {
            // given
            Long memberId = 1L;
            String productId = "invalid";

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.addLike(memberId, productId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("해당 ID의 상품이 존재하지 않습니다");

            verify(likeRepository, never()).save(any(LikeModel.class));
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class RemoveLike {

        @Test
        @DisplayName("좋아요 취소 성공")
        void removeLike_success() {
            // given
            Long memberId = 1L;
            String productId = "prod1";
            ProductModel mockProduct = mock(ProductModel.class);
            when(mockProduct.getId()).thenReturn(100L);
            LikeModel existingLike = LikeModel.create(memberId, 100L);

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.of(mockProduct));
            when(likeRepository.findByRefMemberIdAndRefProductId(any(RefMemberId.class), any(RefProductId.class)))
                    .thenReturn(Optional.of(existingLike));

            // when
            likeService.removeLike(memberId, productId);

            // then
            verify(likeRepository, times(1)).delete(existingLike);
        }

        @Test
        @DisplayName("좋아요가 없어도 예외 발생하지 않음 (멱등성)")
        void removeLike_notExists_noException() {
            // given
            Long memberId = 1L;
            String productId = "prod1";
            ProductModel mockProduct = mock(ProductModel.class);
            when(mockProduct.getId()).thenReturn(100L);

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.of(mockProduct));
            when(likeRepository.findByRefMemberIdAndRefProductId(any(RefMemberId.class), any(RefProductId.class)))
                    .thenReturn(Optional.empty());

            // when
            likeService.removeLike(memberId, productId);

            // then
            verify(likeRepository, never()).delete(any(LikeModel.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 취소 시 예외 발생")
        void removeLike_productNotFound_throwsException() {
            // given
            Long memberId = 1L;
            String productId = "invalid";

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> likeService.removeLike(memberId, productId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("해당 ID의 상품이 존재하지 않습니다");

            verify(likeRepository, never()).delete(any(LikeModel.class));
        }
    }
}
