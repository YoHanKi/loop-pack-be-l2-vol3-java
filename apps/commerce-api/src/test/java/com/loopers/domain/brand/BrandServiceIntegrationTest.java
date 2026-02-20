package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("BrandService 통합 테스트")
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandRepository spyBrandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        Mockito.reset(spyBrandRepository);
    }

    @Test
    @DisplayName("브랜드 생성 성공")
    void createBrand_success() {
        // given
        String brandId = "nike";
        String brandName = "Nike";

        // when
        BrandModel savedBrand = brandService.createBrand(brandId, brandName);

        // then
        verify(spyBrandRepository, times(1)).save(any(BrandModel.class));

        assertAll(
                () -> assertThat(savedBrand).isNotNull(),
                () -> assertThat(savedBrand.getId()).isNotNull(),
                () -> assertThat(savedBrand.getBrandId()).isEqualTo(new BrandId(brandId)),
                () -> assertThat(savedBrand.getBrandName().value()).isEqualTo(brandName),
                () -> assertThat(savedBrand.isDeleted()).isFalse()
        );

        // DB에서 직접 조회하여 검증
        BrandModel foundBrand = brandJpaRepository.findById(savedBrand.getId()).orElseThrow();
        assertAll(
                () -> assertThat(foundBrand.getBrandId()).isEqualTo(new BrandId(brandId)),
                () -> assertThat(foundBrand.getBrandName().value()).isEqualTo(brandName),
                () -> assertThat(foundBrand.isDeleted()).isFalse()
        );
    }

    @Test
    @DisplayName("중복된 브랜드 ID로 생성 시 예외 발생")
    void createBrand_duplicateId_throwsException() {
        // given
        String brandId = "adidas";
        brandService.createBrand(brandId, "Adidas");

        // when & then
        assertThatThrownBy(() -> brandService.createBrand(brandId, "Adidas2"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("이미 존재하는 브랜드 ID입니다.")
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("브랜드 삭제 성공 (soft delete)")
    void deleteBrand_success() {
        // given
        String brandId = "puma";
        BrandModel brand = brandService.createBrand(brandId, "Puma");
        assertThat(brand.isDeleted()).isFalse();

        // when
        brandService.deleteBrand(brandId);

        // then
        BrandModel deletedBrand = brandJpaRepository.findByBrandId(new BrandId(brandId)).orElseThrow();
        assertThat(deletedBrand.isDeleted()).isTrue();
        assertThat(deletedBrand.getDeletedAt()).isNotNull();

        // save가 2번 호출됨 (생성 1회 + 삭제 1회)
        verify(spyBrandRepository, times(2)).save(any(BrandModel.class));
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 삭제 시 예외 발생")
    void deleteBrand_notFound_throwsException() {
        // given
        String nonExistentBrandId = "nonexist";

        // when & then
        assertThatThrownBy(() -> brandService.deleteBrand(nonExistentBrandId))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("해당 ID의 브랜드가 존재하지 않습니다.")
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("브랜드 삭제 시 해당 브랜드의 상품도 연쇄 soft delete")
    void deleteBrand_cascadeDeletesProducts() {
        // given
        String brandId = "samsung";
        BrandModel brand = brandService.createBrand(brandId, "Samsung");

        ProductModel product1 = productService.createProduct("prod1", brandId, "Product 1", new BigDecimal("10000"), 10);
        ProductModel product2 = productService.createProduct("prod2", brandId, "Product 2", new BigDecimal("20000"), 20);

        assertThat(product1.isDeleted()).isFalse();
        assertThat(product2.isDeleted()).isFalse();

        // when
        brandService.deleteBrand(brandId);

        // then - 브랜드 삭제됨
        BrandModel deletedBrand = brandJpaRepository.findByBrandId(new BrandId(brandId)).orElseThrow();
        assertThat(deletedBrand.isDeleted()).isTrue();

        // then - 상품도 연쇄 삭제됨
        ProductModel deletedProduct1 = productJpaRepository.findById(product1.getId()).orElseThrow();
        ProductModel deletedProduct2 = productJpaRepository.findById(product2.getId()).orElseThrow();
        assertThat(deletedProduct1.isDeleted()).isTrue();
        assertThat(deletedProduct2.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 삭제된 상품은 브랜드 삭제 시 영향받지 않음")
    void deleteBrand_alreadyDeletedProduct_notAffected() {
        // given
        String brandId = "lg";
        BrandModel brand = brandService.createBrand(brandId, "LG");

        ProductModel product = productService.createProduct("prodlg", brandId, "LG Product", new BigDecimal("50000"), 5);
        productService.deleteProduct("prodlg"); // 미리 삭제

        // when
        brandService.deleteBrand(brandId);

        // then - 브랜드는 삭제됨
        BrandModel deletedBrand = brandJpaRepository.findByBrandId(new BrandId(brandId)).orElseThrow();
        assertThat(deletedBrand.isDeleted()).isTrue();

        // then - 상품 삭제 상태는 그대로
        ProductModel deletedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(deletedProduct.isDeleted()).isTrue();
    }

    @TestConfiguration
    static class SpyConfig {
        @Bean
        @Primary
        public BrandRepository spyBrandRepository(BrandJpaRepository brandJpaRepository) {
            return Mockito.spy(new BrandRepository() {
                @Override
                public BrandModel save(BrandModel brand) {
                    return brandJpaRepository.save(brand);
                }

                @Override
                public Optional<BrandModel> findByBrandId(BrandId brandId) {
                    return brandJpaRepository.findByBrandId(brandId);
                }

                @Override
                public Optional<BrandModel> findById(Long id) {
                    return brandJpaRepository.findById(id);
                }

                @Override
                public boolean existsByBrandId(BrandId brandId) {
                    return brandJpaRepository.existsByBrandId(brandId);
                }
            });
        }
    }
}
