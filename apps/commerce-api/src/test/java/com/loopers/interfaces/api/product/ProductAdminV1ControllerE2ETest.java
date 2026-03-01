package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ProductAdminV1Controller E2E 테스트")
class ProductAdminV1ControllerE2ETest {

    private static final String ADMIN_PRODUCTS_URL = "/api-admin/v1/products";
    private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 50);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE);
        return headers;
    }

    @DisplayName("PUT /api-admin/v1/products/{productId} - 상품 수정")
    @Nested
    class UpdateProduct {

        @Test
        @DisplayName("상품 수정 성공 - 200 OK")
        void updateProduct_success_returns200() {
            // given
            ProductAdminV1Dto.UpdateProductAdminRequest request =
                    new ProductAdminV1Dto.UpdateProductAdminRequest(
                            "Nike Air Max Updated",
                            new BigDecimal("120000"),
                            30,
                            null
                    );

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = restTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/prod1",
                    HttpMethod.PUT,
                    new HttpEntity<>(request, adminHeaders()),
                    responseType
            );

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().productName()).isEqualTo("Nike Air Max Updated"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("120000")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(30)
            );
        }

        @Test
        @DisplayName("어드민 인증 헤더 없으면 403 Forbidden")
        void updateProduct_noAdminHeader_returns403() {
            // given
            ProductAdminV1Dto.UpdateProductAdminRequest request =
                    new ProductAdminV1Dto.UpdateProductAdminRequest(
                            "Updated Name",
                            new BigDecimal("100000"),
                            50,
                            null
                    );

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/prod1",
                    HttpMethod.PUT,
                    new HttpEntity<>(request),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("잘못된 어드민 LDAP 값이면 403 Forbidden")
        void updateProduct_invalidLdap_returns403() {
            // given
            ProductAdminV1Dto.UpdateProductAdminRequest request =
                    new ProductAdminV1Dto.UpdateProductAdminRequest(
                            "Updated Name",
                            new BigDecimal("100000"),
                            50,
                            null
                    );

            HttpHeaders invalidHeaders = new HttpHeaders();
            invalidHeaders.set(ADMIN_LDAP_HEADER, "invalid.user");

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/prod1",
                    HttpMethod.PUT,
                    new HttpEntity<>(request, invalidHeaders),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("brandId 변경 시도 시 400 Bad Request")
        void updateProduct_brandIdChangeAttempt_returns400() {
            // given
            ProductAdminV1Dto.UpdateProductAdminRequest request =
                    new ProductAdminV1Dto.UpdateProductAdminRequest(
                            "Updated Name",
                            new BigDecimal("100000"),
                            50,
                            "adidas"
                    );

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/prod1",
                    HttpMethod.PUT,
                    new HttpEntity<>(request, adminHeaders()),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 상품 수정 시 404 Not Found")
        void updateProduct_notFound_returns404() {
            // given
            ProductAdminV1Dto.UpdateProductAdminRequest request =
                    new ProductAdminV1Dto.UpdateProductAdminRequest(
                            "Updated Name",
                            new BigDecimal("100000"),
                            50,
                            null
                    );

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/nonexistent",
                    HttpMethod.PUT,
                    new HttpEntity<>(request, adminHeaders()),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("가격이 음수이면 400 Bad Request")
        void updateProduct_negativePrice_returns400() {
            // given
            ProductAdminV1Dto.UpdateProductAdminRequest request =
                    new ProductAdminV1Dto.UpdateProductAdminRequest(
                            "Updated Name",
                            new BigDecimal("-1000"),
                            50,
                            null
                    );

            // when
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    ADMIN_PRODUCTS_URL + "/prod1",
                    HttpMethod.PUT,
                    new HttpEntity<>(request, adminHeaders()),
                    ApiResponse.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
