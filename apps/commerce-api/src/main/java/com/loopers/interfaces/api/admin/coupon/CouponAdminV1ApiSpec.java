package com.loopers.interfaces.api.admin.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Admin Coupon", description = "어드민 쿠폰 관리 API")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회")
    ResponseEntity<ApiResponse<Page<CouponAdminV1Dto.TemplateResponse>>> getAllTemplates(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            Pageable pageable
    );

    @Operation(summary = "쿠폰 템플릿 상세 조회")
    ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateResponse>> getTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId
    );

    @Operation(summary = "쿠폰 템플릿 생성")
    ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateResponse>> createTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @RequestBody CouponAdminV1Dto.CreateTemplateRequest request
    );

    @Operation(summary = "쿠폰 템플릿 수정")
    ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateResponse>> updateTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId,
            @RequestBody CouponAdminV1Dto.UpdateTemplateRequest request
    );

    @Operation(summary = "쿠폰 템플릿 삭제")
    ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId
    );

    @Operation(summary = "쿠폰 발급 내역 조회")
    ResponseEntity<ApiResponse<CouponAdminV1Dto.IssueListResponse>> getIssuedCoupons(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId
    );
}
