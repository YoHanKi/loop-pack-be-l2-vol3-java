package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandService;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Product API E2E 테스트")
class ProductV1ControllerE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String ENDPOINT_PRODUCTS_CURSOR = "/api/v1/products/cursor";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/products")
    @Nested
    class CreateProduct {

        @Test
        @DisplayName("상품 생성 성공 시 201 Created와 생성된 상품 정보 반환")
        void createProduct_success_returns201() {
            // arrange
            brandService.createBrand("nike", "Nike");

            ProductV1Dto.CreateProductRequest request = new ProductV1Dto.CreateProductRequest(
                    "prod1",
                    "nike",
                    "Nike Air Max",
                    new BigDecimal("150000"),
                    100
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS,
                            HttpMethod.POST,
                            new HttpEntity<>(request),
                            responseType
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data()).isNotNull(),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo("prod1"),
                    () -> assertThat(response.getBody().data().refBrandId()).isNotNull(),
                    () -> assertThat(response.getBody().data().productName()).isEqualTo("Nike Air Max"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("150000.00")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().brand()).isNotNull(),
                    () -> assertThat(response.getBody().data().brand().brandId()).isEqualTo("nike"),
                    () -> assertThat(response.getBody().data().brand().brandName()).isEqualTo("Nike"),
                    () -> assertThat(response.getBody().data().likesCount()).isEqualTo(0)
            );
        }

        @Test
        @DisplayName("중복된 상품 ID로 생성 시 409 Conflict 반환")
        void createProduct_duplicateId_returns409() {
            // arrange
            brandService.createBrand("nike", "Nike");

            ProductV1Dto.CreateProductRequest request = new ProductV1Dto.CreateProductRequest(
                    "prod1",
                    "nike",
                    "Nike Air Max",
                    new BigDecimal("150000"),
                    100
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {
                    };

            // 첫 번째 생성
            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // act - 중복 생성 시도
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS,
                            HttpMethod.POST,
                            new HttpEntity<>(request),
                            responseType
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("존재하지 않는 브랜드로 생성 시 404 Not Found 반환")
        void createProduct_nonExistentBrand_returns404() {
            // arrange
            ProductV1Dto.CreateProductRequest request = new ProductV1Dto.CreateProductRequest(
                    "prod1",
                    "nobrand",
                    "Product",
                    new BigDecimal("10000"),
                    10
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS,
                            HttpMethod.POST,
                            new HttpEntity<>(request),
                            responseType
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @Test
        @DisplayName("상품 목록 조회 성공")
        void getProducts_success_returns200() {
            // arrange
            brandService.createBrand("nike", "Nike");

            ProductV1Dto.CreateProductRequest request1 = new ProductV1Dto.CreateProductRequest(
                    "prod1", "nike", "Product 1", new BigDecimal("10000"), 10
            );
            ProductV1Dto.CreateProductRequest request2 = new ProductV1Dto.CreateProductRequest(
                    "prod2", "nike", "Product 2", new BigDecimal("20000"), 20
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> createResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request1), createResponseType);
            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request2), createResponseType);

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> listResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS + "?page=0&size=10&sort=latest",
                            HttpMethod.GET,
                            null,
                            listResponseType
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().products()).hasSize(2),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("브랜드 필터링 동작")
        void getProducts_filterByBrand_success() {
            // arrange
            brandService.createBrand("nike", "Nike");
            brandService.createBrand("adidas", "Adidas");

            ProductV1Dto.CreateProductRequest nikeProduct = new ProductV1Dto.CreateProductRequest(
                    "prod1", "nike", "Nike Product", new BigDecimal("10000"), 10
            );
            ProductV1Dto.CreateProductRequest adidasProduct = new ProductV1Dto.CreateProductRequest(
                    "prod2", "adidas", "Adidas Product", new BigDecimal("20000"), 20
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> createResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(nikeProduct), createResponseType);
            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(adidasProduct), createResponseType);

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> listResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS + "?brandId=nike",
                            HttpMethod.GET,
                            null,
                            listResponseType
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().products()).hasSize(1),
                    () -> assertThat(response.getBody().data().products().get(0).refBrandId()).isNotNull()
            );
        }

        @Test
        @DisplayName("price_asc 정렬 동작")
        void getProducts_sortByPriceAsc_success() {
            // arrange
            brandService.createBrand("nike", "Nike");

            ProductV1Dto.CreateProductRequest expensive = new ProductV1Dto.CreateProductRequest(
                    "prod1", "nike", "Expensive", new BigDecimal("100000"), 10
            );
            ProductV1Dto.CreateProductRequest cheap = new ProductV1Dto.CreateProductRequest(
                    "prod2", "nike", "Cheap", new BigDecimal("10000"), 20
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> createResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(expensive), createResponseType);
            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(cheap), createResponseType);

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> listResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS + "?sort=price_asc",
                            HttpMethod.GET,
                            null,
                            listResponseType
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().products()).hasSize(2),
                    () -> assertThat(response.getBody().data().products().get(0).price())
                            .isLessThan(response.getBody().data().products().get(1).price())
            );
        }
    }

    @DisplayName("GET /api/v1/products/cursor")
    @Nested
    class GetProductsByCursor {

        private final ParameterizedTypeReference<ApiResponse<ProductV1Dto.CursorListResponse>> cursorResponseType =
                new ParameterizedTypeReference<>() {};

        private final ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> createResponseType =
                new ParameterizedTypeReference<>() {};

        private void createProduct(String productId, String brandId, String name, BigDecimal price) {
            ProductV1Dto.CreateProductRequest request = new ProductV1Dto.CreateProductRequest(productId, brandId, name, price, 10);
            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request), createResponseType);
        }

        @Test
        @DisplayName("첫 페이지 조회 시 200 반환, hasNext=true, nextCursor 존재")
        void firstPage_returns200WithNextCursor() {
            // arrange
            brandService.createBrand("nike", "Nike");
            for (int i = 1; i <= 15; i++) {
                createProduct("prod" + i, "nike", "Product " + i, new BigDecimal(i * 1000));
            }

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&size=10",
                    HttpMethod.GET, null, cursorResponseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).hasSize(10),
                    () -> assertThat(response.getBody().data().hasNext()).isTrue(),
                    () -> assertThat(response.getBody().data().nextCursor()).isNotNull()
            );
        }

        @Test
        @DisplayName("nextCursor로 다음 페이지 조회 시 연속 데이터 반환")
        void nextPage_returnsContinuousData() {
            // arrange
            brandService.createBrand("nike", "Nike");
            for (int i = 1; i <= 15; i++) {
                createProduct("prod" + i, "nike", "Product " + i, new BigDecimal(i * 1000));
            }

            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> firstResponse = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&size=10",
                    HttpMethod.GET, null, cursorResponseType
            );
            String nextCursor = firstResponse.getBody().data().nextCursor();

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> secondResponse = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&size=10&cursor=" + nextCursor,
                    HttpMethod.GET, null, cursorResponseType
            );

            List<Long> firstIds = firstResponse.getBody().data().items().stream().map(ProductV1Dto.ProductResponse::id).toList();
            List<Long> secondIds = secondResponse.getBody().data().items().stream().map(ProductV1Dto.ProductResponse::id).toList();

            // assert
            assertAll(
                    () -> assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(secondResponse.getBody().data().items()).hasSize(5),
                    () -> assertThat(secondResponse.getBody().data().hasNext()).isFalse(),
                    () -> assertThat(secondResponse.getBody().data().nextCursor()).isNull(),
                    () -> assertThat(firstIds).doesNotContainAnyElementsOf(secondIds)
            );
        }

        @Test
        @DisplayName("전체 순회 시 모든 아이템이 정확히 한 번씩 조회됨")
        void fullTraversal_allItemsExactlyOnce() {
            // arrange
            brandService.createBrand("nike", "Nike");
            for (int i = 1; i <= 12; i++) {
                createProduct("prod" + i, "nike", "Product " + i, new BigDecimal(i * 1000));
            }

            // act
            List<Long> allIds = new ArrayList<>();
            String cursor = null;
            do {
                String url = ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&size=5"
                        + (cursor != null ? "&cursor=" + cursor : "");
                ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> response = testRestTemplate.exchange(
                        url, HttpMethod.GET, null, cursorResponseType
                );
                response.getBody().data().items().forEach(item -> allIds.add(item.id()));
                cursor = response.getBody().data().nextCursor();
            } while (cursor != null);

            // assert
            assertThat(allIds).hasSize(12);
            assertThat(allIds).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("브랜드 필터 + 커서 조합 정상 동작")
        void brandFilter_withCursor_works() {
            // arrange
            brandService.createBrand("nike", "Nike");
            brandService.createBrand("adidas", "Adidas");
            for (int i = 1; i <= 8; i++) {
                createProduct("nike" + i, "nike", "Nike " + i, new BigDecimal(i * 1000));
            }
            for (int i = 1; i <= 5; i++) {
                createProduct("adidas" + i, "adidas", "Adidas " + i, new BigDecimal(i * 1000));
            }

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> firstPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&brandId=nike&size=5",
                    HttpMethod.GET, null, cursorResponseType
            );
            String nextCursor = firstPage.getBody().data().nextCursor();
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> secondPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&brandId=nike&size=5&cursor=" + nextCursor,
                    HttpMethod.GET, null, cursorResponseType
            );

            // assert: nike 상품 8개가 두 페이지로 정확히 분리
            assertAll(
                    () -> assertThat(firstPage.getBody().data().items()).hasSize(5),
                    () -> assertThat(firstPage.getBody().data().hasNext()).isTrue(),
                    () -> assertThat(secondPage.getBody().data().items()).hasSize(3),
                    () -> assertThat(secondPage.getBody().data().hasNext()).isFalse()
            );
        }

        @Test
        @DisplayName("price_asc 정렬 커서 페이징 - 가격 오름차순 순서 유지")
        void priceAscCursor_maintainsPriceOrder() {
            // arrange
            brandService.createBrand("nike", "Nike");
            createProduct("prod1", "nike", "Product 1", new BigDecimal("50000"));
            createProduct("prod2", "nike", "Product 2", new BigDecimal("10000"));
            createProduct("prod3", "nike", "Product 3", new BigDecimal("30000"));
            createProduct("prod4", "nike", "Product 4", new BigDecimal("20000"));
            createProduct("prod5", "nike", "Product 5", new BigDecimal("40000"));

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> firstPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=price_asc&size=3",
                    HttpMethod.GET, null, cursorResponseType
            );
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> secondPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=price_asc&size=3&cursor=" + firstPage.getBody().data().nextCursor(),
                    HttpMethod.GET, null, cursorResponseType
            );

            List<BigDecimal> allPrices = new ArrayList<>();
            firstPage.getBody().data().items().forEach(item -> allPrices.add(item.price()));
            secondPage.getBody().data().items().forEach(item -> allPrices.add(item.price()));

            // assert
            assertAll(
                    () -> assertThat(allPrices).hasSize(5),
                    () -> assertThat(allPrices).isSortedAccordingTo(BigDecimal::compareTo)
            );
        }

        @Test
        @DisplayName("잘못된 커서 문자열 전달 시 400 반환")
        void invalidCursor_returns400() {
            // arrange
            brandService.createBrand("nike", "Nike");
            createProduct("prod1", "nike", "Product 1", new BigDecimal("10000"));

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&cursor=invalid-cursor-!!",
                    HttpMethod.GET, null, cursorResponseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("정렬 불일치 커서 사용 시 400 반환")
        void mismatchedCursorSort_returns400() {
            // arrange
            brandService.createBrand("nike", "Nike");
            for (int i = 1; i <= 5; i++) {
                createProduct("prod" + i, "nike", "Product " + i, new BigDecimal(i * 1000));
            }

            // latest 커서 획득
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> firstPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=latest&size=3",
                    HttpMethod.GET, null, cursorResponseType
            );
            String latestCursor = firstPage.getBody().data().nextCursor();

            // act: latest 커서를 price_asc 정렬에서 사용
            ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS_CURSOR + "?sort=price_asc&cursor=" + latestCursor,
                    HttpMethod.GET, null, cursorResponseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @Test
        @DisplayName("상품 삭제 성공 시 200 OK 반환")
        void deleteProduct_success_returns200() {
            // arrange
            brandService.createBrand("nike", "Nike");

            ProductV1Dto.CreateProductRequest request = new ProductV1Dto.CreateProductRequest(
                    "prod1", "nike", "Nike Air", new BigDecimal("100000"), 50
            );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> createResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request), createResponseType);

            ParameterizedTypeReference<ApiResponse<Void>> deleteResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS + "/prod1",
                            HttpMethod.DELETE,
                            null,
                            deleteResponseType
                    );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );

            // 삭제 후 목록 조회 시 제외됨 확인
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> listResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> listResponse =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS,
                            HttpMethod.GET,
                            null,
                            listResponseType
                    );

            assertThat(listResponse.getBody().data().products()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 상품 삭제 시 404 Not Found 반환")
        void deleteProduct_nonExistent_returns404() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Void>> deleteResponseType =
                    new ParameterizedTypeReference<>() {
                    };

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_PRODUCTS + "/nonexistent",
                            HttpMethod.DELETE,
                            null,
                            deleteResponseType
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
