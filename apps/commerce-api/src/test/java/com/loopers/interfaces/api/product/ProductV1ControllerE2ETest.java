package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Product API E2E 테스트")
class ProductV1ControllerE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandService brandService;

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
                    () -> assertThat(response.getBody().success()).isEqualTo(true),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo("prod1"),
                    () -> assertThat(response.getBody().data().refBrandId()).isNotNull(),
                    () -> assertThat(response.getBody().data().productName()).isEqualTo("Nike Air Max"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("150000.00")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100)
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
                    () -> assertThat(response.getBody().success()).isEqualTo(true),
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
                    () -> assertThat(response.getBody().success()).isEqualTo(true)
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
