package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ProductService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private ProductService productService;

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        @Test
        @DisplayName("유효한 브랜드로 상품 생성 성공")
        void createProduct_withValidBrand_success() {
            // given
            String productId = "prod1";
            String brandId = "nike";
            String productName = "Nike Air Max";
            BigDecimal price = new BigDecimal("150000");
            int stockQuantity = 100;

            BrandModel mockBrand = mock(BrandModel.class);
            when(mockBrand.getId()).thenReturn(1L);

            when(productRepository.existsByProductId(any(ProductId.class))).thenReturn(false);
            when(brandRepository.findByBrandId(any(BrandId.class))).thenReturn(Optional.of(mockBrand));
            when(productRepository.save(any(ProductModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ProductModel result = productService.createProduct(productId, brandId, productName, price, stockQuantity);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getProductId().value()).isEqualTo(productId);
            assertThat(result.getRefBrandId().value()).isEqualTo(1L);
            verify(productRepository, times(1)).existsByProductId(any(ProductId.class));
            verify(brandRepository, times(1)).findByBrandId(any(BrandId.class));
            verify(productRepository, times(1)).save(any(ProductModel.class));
        }

        @Test
        @DisplayName("중복된 상품 ID로 생성 시 예외 발생")
        void createProduct_withDuplicateId_throwsException() {
            // given
            String productId = "prod1";
            String brandId = "nike";
            String productName = "Nike Air Max";
            BigDecimal price = new BigDecimal("150000");
            int stockQuantity = 100;

            when(productRepository.existsByProductId(any(ProductId.class))).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> productService.createProduct(productId, brandId, productName, price, stockQuantity))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 존재하는 상품 ID입니다");

            verify(productRepository, times(1)).existsByProductId(any(ProductId.class));
            verify(brandRepository, never()).findByBrandId(any(BrandId.class));
            verify(productRepository, never()).save(any(ProductModel.class));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드로 생성 시 예외 발생")
        void createProduct_withNonExistentBrand_throwsException() {
            // given
            String productId = "prod1";
            String brandId = "invalid12"; // 10자 이하로 변경
            String productName = "Nike Air Max";
            BigDecimal price = new BigDecimal("150000");
            int stockQuantity = 100;

            when(productRepository.existsByProductId(any(ProductId.class))).thenReturn(false);
            when(brandRepository.findByBrandId(any(BrandId.class))).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.createProduct(productId, brandId, productName, price, stockQuantity))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("해당 ID의 브랜드가 존재하지 않습니다");

            verify(productRepository, times(1)).existsByProductId(any(ProductId.class));
            verify(brandRepository, times(1)).findByBrandId(any(BrandId.class));
            verify(productRepository, never()).save(any(ProductModel.class));
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @Test
        @DisplayName("존재하는 상품 삭제 성공 (soft delete)")
        void deleteProduct_existingProduct_success() {
            // given
            String productId = "prod1";
            ProductModel product = ProductModel.create(productId, 1L, "Nike Air", new BigDecimal("100000"), 50);

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            productService.deleteProduct(productId);

            // then
            assertThat(product.isDeleted()).isTrue();
            verify(productRepository, times(1)).findByProductId(any(ProductId.class));
            verify(productRepository, times(1)).save(product);
        }

        @Test
        @DisplayName("존재하지 않는 상품 삭제 시 예외 발생")
        void deleteProduct_nonExistentProduct_throwsException() {
            // given
            String productId = "invalidProduct";

            when(productRepository.findByProductId(any(ProductId.class))).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.deleteProduct(productId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품이 존재하지 않습니다");

            verify(productRepository, times(1)).findByProductId(any(ProductId.class));
            verify(productRepository, never()).save(any(ProductModel.class));
        }
    }
}
