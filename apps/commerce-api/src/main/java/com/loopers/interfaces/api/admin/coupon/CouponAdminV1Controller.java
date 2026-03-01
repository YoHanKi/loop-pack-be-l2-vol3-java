package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponApp;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final CouponApp couponApp;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<Page<CouponAdminV1Dto.TemplateResponse>>> getAllTemplates(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            Pageable pageable
    ) {
        validateAdmin(ldapHeader);
        Page<CouponTemplateInfo> templates = couponApp.getAllTemplates(pageable);
        return ResponseEntity.ok(ApiResponse.success(templates.map(CouponAdminV1Dto.TemplateResponse::from)));
    }

    @GetMapping("/{couponId}")
    @Override
    public ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateResponse>> getTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId
    ) {
        validateAdmin(ldapHeader);
        CouponTemplateInfo template = couponApp.getTemplate(couponId);
        return ResponseEntity.ok(ApiResponse.success(CouponAdminV1Dto.TemplateResponse.from(template)));
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateResponse>> createTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @Valid @RequestBody CouponAdminV1Dto.CreateTemplateRequest request
    ) {
        validateAdmin(ldapHeader);
        CouponTemplateInfo template = couponApp.createTemplate(
                request.name(), request.type(), request.value(),
                request.minOrderAmount(), request.expiredAt(), request.totalQuantity()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(CouponAdminV1Dto.TemplateResponse.from(template)));
    }

    @PutMapping("/{couponId}")
    @Override
    public ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateResponse>> updateTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId,
            @Valid @RequestBody CouponAdminV1Dto.UpdateTemplateRequest request
    ) {
        validateAdmin(ldapHeader);
        CouponTemplateInfo template = couponApp.updateTemplate(
                couponId, request.name(), request.value(),
                request.minOrderAmount(), request.expiredAt(), request.totalQuantity()
        );
        return ResponseEntity.ok(ApiResponse.success(CouponAdminV1Dto.TemplateResponse.from(template)));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId
    ) {
        validateAdmin(ldapHeader);
        couponApp.deleteTemplate(couponId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ResponseEntity<ApiResponse<CouponAdminV1Dto.IssueListResponse>> getIssuedCoupons(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String couponId
    ) {
        validateAdmin(ldapHeader);
        List<UserCouponInfo> userCoupons = couponApp.getIssuedCoupons(couponId);
        return ResponseEntity.ok(ApiResponse.success(CouponAdminV1Dto.IssueListResponse.from(userCoupons)));
    }

    private void validateAdmin(String ldapHeader) {
        if (!ADMIN_LDAP_VALUE.equals(ldapHeader)) {
            throw new CoreException(ErrorType.FORBIDDEN, "어드민 권한이 필요합니다.");
        }
    }
}
