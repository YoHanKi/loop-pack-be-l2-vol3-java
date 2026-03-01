package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final ProductFacade productFacade;

    @PutMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> updateProduct(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String productId,
            @Valid @RequestBody ProductAdminV1Dto.UpdateProductAdminRequest request
    ) {
        if (!ADMIN_LDAP_VALUE.equals(ldapHeader)) {
            throw new CoreException(ErrorType.FORBIDDEN, "어드민 권한이 필요합니다.");
        }

        if (request.brandId() != null && !request.brandId().isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "brandId는 변경할 수 없습니다.");
        }

        ProductInfo info = productFacade.updateProduct(
                productId,
                request.productName(),
                request.price(),
                request.stockQuantity()
        );
        return ResponseEntity.ok(ApiResponse.success(ProductV1Dto.ProductResponse.from(info)));
    }
}
