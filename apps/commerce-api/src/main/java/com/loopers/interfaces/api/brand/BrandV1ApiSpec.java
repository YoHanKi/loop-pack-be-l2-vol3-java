package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "브랜드 관리 API", description = "브랜드 관련 API")
public interface BrandV1ApiSpec {

    @Operation(
            summary = "브랜드 생성",
            description = "새로운 브랜드를 생성합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> createBrand(
            @Schema(name = "브랜드 생성 요청 DTO", description = "브랜드 생성에 필요한 정보를 담고 있는 DTO")
            @Valid @RequestBody BrandV1Dto.CreateBrandRequest request
    );

    @Operation(
            summary = "브랜드 삭제",
            description = "브랜드를 삭제합니다 (soft delete). 상품이 참조하고 있는 경우 삭제할 수 없습니다."
    )
    ApiResponse<Void> deleteBrand(
            @Parameter(description = "브랜드 ID") @PathVariable String brandId
    );
}
