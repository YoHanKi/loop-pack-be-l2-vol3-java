package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("ProductService 통합 테스트")
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        @Test
        @DisplayName("유효한 브랜드로 상품 생성 성공")
        void createProduct_withValidBrand_success() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            String productId = "prod1";
            String productName = "Nike Air Max";
            BigDecimal price = new BigDecimal("150000");
            int stockQuantity = 100;

            // when
            ProductModel savedProduct = productService.createProduct(productId, brand.getBrandId().value(), productName, price, stockQuantity);

            // then
            assertAll(
                    () -> assertThat(savedProduct).isNotNull(),
                    () -> assertThat(savedProduct.getId()).isNotNull(),
                    () -> assertThat(savedProduct.getProductId().value()).isEqualTo(productId),
                    () -> assertThat(savedProduct.getRefBrandId()).isEqualTo(brand.getId()),
                    () -> assertThat(savedProduct.getProductName().value()).isEqualTo(productName),
                    () -> assertThat(savedProduct.getPrice().value()).isEqualByComparingTo(price.setScale(2)),
                    () -> assertThat(savedProduct.getStockQuantity().value()).isEqualTo(stockQuantity),
                    () -> assertThat(savedProduct.isDeleted()).isFalse()
            );

            // DB에서 직접 조회하여 검증
            ProductModel foundProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertThat(foundProduct.getProductId().value()).isEqualTo(productId);
        }

        @Test
        @DisplayName("중복된 상품 ID로 생성 시 예외 발생")
        void createProduct_withDuplicateId_throwsException() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            String productId = "prod1";
            productService.createProduct(productId, brand.getBrandId().value(), "Nike Air Max", new BigDecimal("150000"), 100);

            // when & then
            assertThatThrownBy(() -> productService.createProduct(productId, brand.getBrandId().value(), "Nike Air Max 2", new BigDecimal("200000"), 50))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 존재하는 상품 ID입니다");
        }

        @Test
        @DisplayName("존재하지 않는 브랜드로 생성 시 예외 발생")
        void createProduct_withNonExistentBrand_throwsException() {
            // given
            String productId = "prod1";
            String invalidBrandId = "nobrand"; // 유효한 형식이지만 존재하지 않는 brandId (10자 이내)

            // when & then
            assertThatThrownBy(() -> productService.createProduct(productId, invalidBrandId, "Product", new BigDecimal("10000"), 10))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드가 존재하지 않습니다");
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @Test
        @DisplayName("존재하는 상품 삭제 성공 (soft delete)")
        void deleteProduct_existingProduct_success() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product = productService.createProduct("prod1", brand.getBrandId().value(), "Nike Air", new BigDecimal("100000"), 50);
            assertThat(product.isDeleted()).isFalse();

            // when
            productService.deleteProduct(product.getProductId().value());

            // then
            ProductModel deletedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(deletedProduct.isDeleted()).isTrue();
            assertThat(deletedProduct.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 상품 삭제 시 예외 발생")
        void deleteProduct_nonExistentProduct_throwsException() {
            // given
            String invalidProductId = "invalidProduct";

            // when & then
            assertThatThrownBy(() -> productService.deleteProduct(invalidProductId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품이 존재하지 않습니다");
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @Test
        @DisplayName("삭제되지 않은 상품만 조회됨")
        void getProducts_excludesDeletedProducts() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product1 = productService.createProduct("prod1", brand.getBrandId().value(), "Product 1", new BigDecimal("10000"), 10);
            ProductModel product2 = productService.createProduct("prod2", brand.getBrandId().value(), "Product 2", new BigDecimal("20000"), 20);
            ProductModel product3 = productService.createProduct("prod3", brand.getBrandId().value(), "Product 3", new BigDecimal("30000"), 30);

            // product2 삭제
            productService.deleteProduct(product2.getProductId().value());

            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<ProductModel> products = productService.getProducts(null, "latest", pageable);

            // then
            assertThat(products.getContent()).hasSize(2);
            assertThat(products.getContent())
                    .extracting(p -> p.getProductId().value())
                    .containsExactlyInAnyOrder("prod1", "prod3")
                    .doesNotContain("prod2");
        }

        @Test
        @DisplayName("브랜드 필터링 동작")
        void getProducts_filtersByBrand() {
            // given
            BrandModel nike = brandService.createBrand("nike", "Nike");
            BrandModel adidas = brandService.createBrand("adidas", "Adidas");

            productService.createProduct("prod1", nike.getBrandId().value(), "Nike Product", new BigDecimal("10000"), 10);
            productService.createProduct("prod2", adidas.getBrandId().value(), "Adidas Product", new BigDecimal("20000"), 20);
            productService.createProduct("prod3", nike.getBrandId().value(), "Nike Product 2", new BigDecimal("30000"), 30);

            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<ProductModel> nikeProducts = productService.getProducts(nike.getBrandId().value(), "latest", pageable);

            // then
            assertThat(nikeProducts.getContent()).hasSize(2);
            assertThat(nikeProducts.getContent())
                    .allMatch(p -> p.getRefBrandId().equals(nike.getId()));
        }

        @Test
        @DisplayName("latest 정렬 (updatedAt DESC)")
        void getProducts_sortByLatest() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            ProductModel product1 = productService.createProduct("prod1", brand.getBrandId().value(), "Product 1", new BigDecimal("10000"), 10);
            ProductModel product2 = productService.createProduct("prod2", brand.getBrandId().value(), "Product 2", new BigDecimal("20000"), 20);

            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<ProductModel> products = productService.getProducts(null, "latest", pageable);

            // then
            assertThat(products.getContent()).hasSize(2);
            // 최신 생성된 상품이 먼저 (updatedAt DESC)
            assertThat(products.getContent().get(0).getUpdatedAt())
                    .isAfterOrEqualTo(products.getContent().get(1).getUpdatedAt());
        }

        @Test
        @DisplayName("price_asc 정렬 (가격 오름차순)")
        void getProducts_sortByPriceAsc() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            productService.createProduct("prod1", brand.getBrandId().value(), "Expensive", new BigDecimal("100000"), 10);
            productService.createProduct("prod2", brand.getBrandId().value(), "Cheap", new BigDecimal("10000"), 20);
            productService.createProduct("prod3", brand.getBrandId().value(), "Medium", new BigDecimal("50000"), 30);

            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<ProductModel> products = productService.getProducts(null, "price_asc", pageable);

            // then
            assertThat(products.getContent()).hasSize(3);
            assertThat(products.getContent())
                    .extracting(p -> p.getPrice().value())
                    .containsExactly(
                            new BigDecimal("10000.00"),
                            new BigDecimal("50000.00"),
                            new BigDecimal("100000.00")
                    );
        }

        @Test
        @DisplayName("페이징 동작")
        void getProducts_pagination() {
            // given
            BrandModel brand = brandService.createBrand("nike", "Nike");
            for (int i = 1; i <= 15; i++) {
                productService.createProduct("prod" + i, brand.getBrandId().value(), "Product " + i, new BigDecimal(i * 1000), i);
            }

            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<ProductModel> firstPage = productService.getProducts(null, "latest", pageable);
            Page<ProductModel> secondPage = productService.getProducts(null, "latest", PageRequest.of(1, 10));

            // then
            assertThat(firstPage.getContent()).hasSize(10);
            assertThat(secondPage.getContent()).hasSize(5);
            assertThat(firstPage.getTotalElements()).isEqualTo(15);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
        }
    }
}
