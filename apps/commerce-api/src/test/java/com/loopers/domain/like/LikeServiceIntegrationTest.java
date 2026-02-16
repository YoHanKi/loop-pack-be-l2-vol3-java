package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("LikeService 통합 테스트")
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 추가할 때,")
    @Nested
    class AddLike {

        @Test
        @DisplayName("좋아요 추가 성공")
        void addLike_success() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
            Long memberId = 1L;

            // when
            LikeModel like = likeService.addLike(memberId, "prod1");

            // then
            assertThat(like).isNotNull();
            assertThat(like.getRefMemberId().value()).isEqualTo(memberId);
            assertThat(like.getRefProductId().value()).isEqualTo(product.getId());
        }

        @Test
        @DisplayName("중복 좋아요 추가 시 기존 좋아요 반환 (멱등성)")
        void addLike_duplicate_returnsExisting() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
            Long memberId = 1L;

            // when
            LikeModel firstLike = likeService.addLike(memberId, "prod1");
            LikeModel secondLike = likeService.addLike(memberId, "prod1");

            // then
            assertThat(firstLike.getId()).isEqualTo(secondLike.getId());
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 추가 시 예외 발생")
        void addLike_productNotFound_throwsException() {
            // given
            Long memberId = 1L;

            // when & then
            assertThatThrownBy(() -> likeService.addLike(memberId, "invalid"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("해당 ID의 상품이 존재하지 않습니다");
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class RemoveLike {

        @Test
        @DisplayName("좋아요 취소 성공")
        void removeLike_success() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
            Long memberId = 1L;
            likeService.addLike(memberId, "prod1");

            // when
            likeService.removeLike(memberId, "prod1");

            // then - 중복 취소해도 예외 발생하지 않음
            likeService.removeLike(memberId, "prod1");
        }

        @Test
        @DisplayName("좋아요가 없어도 예외 발생하지 않음 (멱등성)")
        void removeLike_notExists_noException() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
            Long memberId = 1L;

            // when & then - 예외 발생하지 않음
            likeService.removeLike(memberId, "prod1");
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 취소 시 예외 발생")
        void removeLike_productNotFound_throwsException() {
            // given
            Long memberId = 1L;

            // when & then
            assertThatThrownBy(() -> likeService.removeLike(memberId, "invalid"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("해당 ID의 상품이 존재하지 않습니다");
        }
    }
}
