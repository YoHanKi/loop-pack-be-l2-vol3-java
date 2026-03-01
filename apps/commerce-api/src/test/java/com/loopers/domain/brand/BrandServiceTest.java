package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrandService 단위 테스트")
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    @Test
    @DisplayName("브랜드 생성 - 성공")
    void createBrand_success() {
        // given
        String brandId = "nike";
        String brandName = "Nike";
        BrandModel mockBrand = BrandModel.create(brandId, brandName);

        when(brandRepository.existsByBrandId(any(BrandId.class))).thenReturn(false);
        when(brandRepository.save(any(BrandModel.class))).thenReturn(mockBrand);

        // when
        BrandModel result = brandService.createBrand(brandId, brandName);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getBrandId()).isEqualTo(new BrandId(brandId));
        assertThat(result.getBrandName()).isEqualTo(new BrandName(brandName));

        verify(brandRepository, times(1)).existsByBrandId(any(BrandId.class));
        verify(brandRepository, times(1)).save(any(BrandModel.class));
    }

    @Test
    @DisplayName("브랜드 생성 - 중복 ID로 실패")
    void createBrand_duplicateId_throwsException() {
        // given
        String brandId = "adidas";
        when(brandRepository.existsByBrandId(any(BrandId.class))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> brandService.createBrand(brandId, "Adidas"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("이미 존재하는 브랜드 ID입니다.")
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);

        verify(brandRepository, times(1)).existsByBrandId(any(BrandId.class));
        verify(brandRepository, never()).save(any(BrandModel.class));
    }

    @Test
    @DisplayName("브랜드 삭제 - 성공")
    void deleteBrand_success() {
        // given
        String brandId = "puma";
        BrandModel mockBrand = BrandModel.create(brandId, "Puma");

        when(brandRepository.findByBrandId(any(BrandId.class))).thenReturn(Optional.of(mockBrand));
        when(brandRepository.save(any(BrandModel.class))).thenReturn(mockBrand);

        // when
        brandService.deleteBrand(brandId);

        // then
        verify(brandRepository, times(1)).findByBrandId(any(BrandId.class));
        verify(brandRepository, times(1)).save(mockBrand);
        assertThat(mockBrand.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("브랜드 삭제 - 존재하지 않는 브랜드")
    void deleteBrand_notFound_throwsException() {
        // given
        String brandId = "invalid123"; // 10자 이하로 변경
        when(brandRepository.findByBrandId(any(BrandId.class))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> brandService.deleteBrand(brandId))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("해당 ID의 브랜드가 존재하지 않습니다.")
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                });

        verify(brandRepository, times(1)).findByBrandId(any(BrandId.class));
        verify(brandRepository, never()).save(any(BrandModel.class));
    }
}
