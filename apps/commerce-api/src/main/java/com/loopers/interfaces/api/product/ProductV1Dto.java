package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class ProductV1Dto {

    public record CreateProductRequest(
            @NotBlank(message = "상품 ID는 필수입니다")
            @Pattern(regexp = "^[A-Za-z0-9]{1,20}$", message = "상품 ID는 영문+숫자, 1~20자여야 합니다")
            String productId,

            @NotBlank(message = "브랜드 ID는 필수입니다")
            @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "브랜드 ID는 영문+숫자, 1~10자여야 합니다")
            String brandId,

            @NotBlank(message = "상품명은 필수입니다")
            @Size(min = 1, max = 100, message = "상품명은 1~100자여야 합니다")
            String productName,

            @NotNull(message = "가격은 필수입니다")
            @DecimalMin(value = "0.0", inclusive = true, message = "가격은 0 이상이어야 합니다")
            BigDecimal price,

            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
            int stockQuantity
    ) {
    }

    public record ProductResponse(
            Long id,
            String productId,
            String brandId,
            String productName,
            BigDecimal price,
            int stockQuantity
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.productId(),
                    info.brandId(),
                    info.productName(),
                    info.price(),
                    info.stockQuantity()
            );
        }
    }

    public record ProductListResponse(
            java.util.List<ProductResponse> products,
            int currentPage,
            int pageSize,
            long totalElements,
            int totalPages
    ) {
        public static ProductListResponse from(org.springframework.data.domain.Page<ProductInfo> page) {
            return new ProductListResponse(
                    page.getContent().stream()
                            .map(ProductResponse::from)
                            .toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
