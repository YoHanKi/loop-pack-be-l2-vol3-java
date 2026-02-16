package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("BrandV1Controller E2E 테스트")
class BrandV1ControllerE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("브랜드 생성 → 삭제 전체 플로우")
    void brandLifecycle() {
        // given
        BrandV1Dto.CreateBrandRequest createRequest = new BrandV1Dto.CreateBrandRequest("nike", "Nike");

        // when - 브랜드 생성
        ResponseEntity<ApiResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/brands",
                createRequest,
                ApiResponse.class
        );

        // then - 생성 성공
        assertAll(
                () -> assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(createResponse.getBody()).isNotNull(),
                () -> assertThat(createResponse.getBody().success()).isEqualTo(true)
        );

        // when - 브랜드 삭제
        ResponseEntity<ApiResponse> deleteResponse = restTemplate.exchange(
                "/api/v1/brands/nike",
                HttpMethod.DELETE,
                null,
                ApiResponse.class
        );

        // then - 삭제 성공
        assertAll(
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(deleteResponse.getBody()).isNotNull(),
                () -> assertThat(deleteResponse.getBody().success()).isEqualTo(true)
        );
    }

    @Test
    @DisplayName("중복된 브랜드 ID로 생성 시 409 Conflict")
    void createBrand_duplicate_returns409() {
        // given
        BrandV1Dto.CreateBrandRequest request = new BrandV1Dto.CreateBrandRequest("adidas", "Adidas");
        restTemplate.postForEntity("/api/v1/brands", request, ApiResponse.class);

        // when - 동일한 ID로 재생성
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/brands",
                request,
                ApiResponse.class
        );

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().success()).isEqualTo(false)
        );
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 삭제 시 404 Not Found")
    void deleteBrand_notFound_returns404() {
        // when
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/brands/nonexistent",
                HttpMethod.DELETE,
                null,
                ApiResponse.class
        );

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().success()).isEqualTo(false)
        );
    }

    @Test
    @DisplayName("유효하지 않은 요청 데이터로 생성 시 400 Bad Request")
    void createBrand_invalidRequest_returns400() {
        // given - brandId가 빈 문자열
        BrandV1Dto.CreateBrandRequest invalidRequest = new BrandV1Dto.CreateBrandRequest("", "Nike");

        // when
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/brands",
                invalidRequest,
                ApiResponse.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
