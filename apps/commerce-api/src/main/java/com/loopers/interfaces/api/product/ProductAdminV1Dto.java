package com.loopers.interfaces.api.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductAdminV1Dto {

    public record UpdateProductAdminRequest(
            @NotBlank(message = "상품명은 필수입니다")
            @Size(min = 1, max = 100, message = "상품명은 1~100자여야 합니다")
            String productName,

            @NotNull(message = "가격은 필수입니다")
            @DecimalMin(value = "0.0", inclusive = true, message = "가격은 0 이상이어야 합니다")
            BigDecimal price,

            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
            int stockQuantity,

            String brandId
    ) {}
}
