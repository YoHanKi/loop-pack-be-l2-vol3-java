package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("like_count 동기화 정합성 통합 테스트")
class LikeCountSyncIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel freshProduct(ProductModel product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    @Test
    @DisplayName("addLike 호출 시 like_count가 1 증가한다")
    void addLike_incrementsLikeCount() {
        // given
        BrandModel brand = brandService.createBrand("nike", "Nike");
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);

        // when
        likeService.addLike(1L, "prod1");

        // then
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 active 상태인 좋아요를 중복 추가해도 like_count는 변하지 않는다")
    void addLike_duplicate_doesNotChangeLikeCount() {
        // given
        BrandModel brand = brandService.createBrand("nike", "Nike");
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeService.addLike(1L, "prod1");
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);

        // when
        likeService.addLike(1L, "prod1"); // 중복 추가

        // then
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("removeLike 호출 시 like_count가 1 감소한다")
    void removeLike_decrementsLikeCount() {
        // given
        BrandModel brand = brandService.createBrand("nike", "Nike");
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeService.addLike(1L, "prod1");
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);

        // when
        likeService.removeLike(1L, "prod1");

        // then
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("이미 취소된 좋아요를 중복 취소해도 like_count는 0 아래로 내려가지 않는다")
    void removeLike_duplicate_doesNotGoBelowZero() {
        // given
        BrandModel brand = brandService.createBrand("nike", "Nike");
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeService.addLike(1L, "prod1");
        likeService.removeLike(1L, "prod1");
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);

        // when
        likeService.removeLike(1L, "prod1"); // 중복 취소

        // then
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("soft-delete된 좋아요를 재추가(복원)하면 like_count가 1 증가한다")
    void addLike_restore_incrementsLikeCount() {
        // given
        BrandModel brand = brandService.createBrand("nike", "Nike");
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeService.addLike(1L, "prod1");    // like_count = 1
        likeService.removeLike(1L, "prod1"); // like_count = 0 (soft-delete)
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);

        // when
        likeService.addLike(1L, "prod1"); // soft-delete된 레코드 복원

        // then
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("add → remove → add 시퀀스 후 like_count는 1이다")
    void addRemoveAdd_sequence_finalLikeCountIsOne() {
        // given
        BrandModel brand = brandService.createBrand("nike", "Nike");
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        // when
        likeService.addLike(1L, "prod1");    // like_count = 1
        likeService.removeLike(1L, "prod1"); // like_count = 0
        likeService.addLike(1L, "prod1");    // like_count = 1 (복원)

        // then
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);
    }
}
