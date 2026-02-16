package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
    private BrandReader brandReader;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

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
        BrandModel deletedBrand = brandReader.getOrThrow(brandId);
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

    // TODO: Product 도메인 구현 후 추가할 테스트
    // @Test
    // @DisplayName("상품이 참조하고 있는 브랜드 삭제 시 예외 발생")
    // void deleteBrand_hasProducts_throwsException() {
    //     // given
    //     String brandId = "samsung";
    //     brandService.createBrand(brandId, "Samsung");
    //     // productService.createProduct(..., brandId, ...); // Product 생성
    //
    //     // when & then
    //     assertThatThrownBy(() -> brandService.deleteBrand(brandId))
    //             .isInstanceOf(CoreException.class)
    //             .hasMessageContaining("해당 브랜드를 참조하는 상품이 존재하여 삭제할 수 없습니다.")
    //             .extracting("errorType")
    //             .isEqualTo(ErrorType.CONFLICT);
    // }

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
                public boolean existsByBrandId(BrandId brandId) {
                    return brandJpaRepository.existsByBrandId(brandId);
                }
            });
        }
    }
}
