package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static com.loopers.interfaces.api.like.LikeV1Dto.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Like API E2E 테스트")
class LikeV1ControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private String baseUrl(String productId) {
        return "http://localhost:" + port + "/api/v1/products/" + productId + "/likes";
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class AddLike {

        @Test
        @DisplayName("좋아요 추가 성공 시 201 Created와 생성된 좋아요 정보 반환")
        void addLike_success_returns201() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

            AddLikeRequest request = new AddLikeRequest(1L);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    baseUrl("prod1"),
                    request,
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS);
        }

        @Test
        @DisplayName("중복 좋아요 추가 시 201 Created 반환 (멱등성)")
        void addLike_duplicate_returns201() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

            AddLikeRequest request = new AddLikeRequest(1L);

            // when
            ResponseEntity<ApiResponse> firstResponse = restTemplate.postForEntity(baseUrl("prod1"), request, ApiResponse.class);
            ResponseEntity<ApiResponse> secondResponse = restTemplate.postForEntity(baseUrl("prod1"), request, ApiResponse.class);

            // then
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 추가 시 404 Not Found 반환")
        void addLike_productNotFound_returns404() {
            // given
            AddLikeRequest request = new AddLikeRequest(1L);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    baseUrl("invalid"),
                    request,
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class RemoveLike {

        @Test
        @DisplayName("좋아요 취소 성공 시 204 No Content 반환")
        void removeLike_success_returns204() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

            AddLikeRequest addRequest = new AddLikeRequest(1L);
            restTemplate.postForEntity(baseUrl("prod1"), addRequest, ApiResponse.class);

            RemoveLikeRequest removeRequest = new RemoveLikeRequest(1L);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    baseUrl("prod1"),
                    HttpMethod.DELETE,
                    new HttpEntity<>(removeRequest),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("좋아요가 없어도 204 No Content 반환 (멱등성)")
        void removeLike_notExists_returns204() {
            // given
            brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

            RemoveLikeRequest removeRequest = new RemoveLikeRequest(1L);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    baseUrl("prod1"),
                    HttpMethod.DELETE,
                    new HttpEntity<>(removeRequest),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 취소 시 404 Not Found 반환")
        void removeLike_productNotFound_returns404() {
            // given
            RemoveLikeRequest removeRequest = new RemoveLikeRequest(1L);

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    baseUrl("invalid"),
                    HttpMethod.DELETE,
                    new HttpEntity<>(removeRequest),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
