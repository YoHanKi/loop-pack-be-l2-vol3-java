package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("MyLikeV1Controller E2E 테스트")
class MyLikeV1ControllerE2ETest {

    private static final String MY_LIKES_URL = "/api/v1/users/me/likes";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/users/me/likes - 내 좋아요 목록 조회")
    @Nested
    class GetMyLikes {

        @Test
        @DisplayName("좋아요 목록 조회 성공 - 200 OK")
        void getMyLikedProducts_success_returns200() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air 1", new BigDecimal("100000"), 10);
            productService.createProduct("prod2", "nike", "Nike Air 2", new BigDecimal("200000"), 20);
            Long memberId = 1L;

            likeService.addLike(memberId, "prod1");
            likeService.addLike(memberId, "prod2");

            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = restTemplate.exchange(
                    MY_LIKES_URL + "?memberId=" + memberId,
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).hasSize(2)
            );
        }

        @Test
        @DisplayName("삭제된 상품은 좋아요 목록에 포함되지 않음")
        void getMyLikedProducts_excludesDeletedProducts() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air 1", new BigDecimal("100000"), 10);
            productService.createProduct("prod2", "nike", "Nike Air 2", new BigDecimal("200000"), 20);
            Long memberId = 1L;

            likeService.addLike(memberId, "prod1");
            likeService.addLike(memberId, "prod2");

            // 상품 삭제
            productService.deleteProduct("prod2");

            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = restTemplate.exchange(
                    MY_LIKES_URL + "?memberId=" + memberId,
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).hasSize(1),
                    () -> assertThat(response.getBody().data().get(0).productId()).isEqualTo("prod1")
            );
        }

        @Test
        @DisplayName("좋아요 목록에 상품명, 브랜드명, 가격 정보 포함")
        void getMyLikedProducts_containsProductInfo() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air Max", new BigDecimal("150000"), 10);
            Long memberId = 1L;
            likeService.addLike(memberId, "prod1");

            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = restTemplate.exchange(
                    MY_LIKES_URL + "?memberId=" + memberId,
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            LikeV1Dto.LikedProductResponse item = response.getBody().data().get(0);
            assertAll(
                    () -> assertThat(item.productId()).isEqualTo("prod1"),
                    () -> assertThat(item.productName()).isEqualTo("Nike Air Max"),
                    () -> assertThat(item.brandName()).isEqualTo("Nike"),
                    () -> assertThat(item.price()).isEqualByComparingTo(new BigDecimal("150000")),
                    () -> assertThat(item.likedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("좋아요가 없으면 빈 목록 반환 - 200 OK")
        void getMyLikedProducts_noLikes_returnsEmpty() {
            // when
            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = restTemplate.exchange(
                    MY_LIKES_URL + "?memberId=99",
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }
}
