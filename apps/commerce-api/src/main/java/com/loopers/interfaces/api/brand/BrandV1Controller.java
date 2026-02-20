package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandFacade brandFacade;

    @Override
    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable String brandId) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.fromInfo(info));
    }

    @PostMapping
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(
            @Valid @RequestBody BrandV1Dto.CreateBrandRequest request
    ) {
        BrandInfo info = brandFacade.createBrand(request.brandId(), request.brandName());
        BrandV1Dto.BrandResponse response = BrandV1Dto.BrandResponse.fromInfo(info);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> deleteBrand(@PathVariable String brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
