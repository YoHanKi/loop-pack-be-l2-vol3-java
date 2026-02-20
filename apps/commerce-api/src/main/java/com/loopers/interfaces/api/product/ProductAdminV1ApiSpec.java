package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Product Admin API", description = "어드민 상품 관리 API")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 수정", description = "상품 정보(상품명, 가격, 재고)를 수정합니다. brandId 변경은 허용되지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 또는 brandId 변경 시도"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> updateProduct(
            @Parameter(description = "LDAP 어드민 토큰") @RequestHeader("X-Loopers-Ldap") String ldapHeader,
            @Parameter(description = "상품 ID") @PathVariable String productId,
            @RequestBody ProductAdminV1Dto.UpdateProductAdminRequest request
    );
}
