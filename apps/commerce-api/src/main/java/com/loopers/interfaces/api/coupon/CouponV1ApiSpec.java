package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Coupon", description = "쿠폰 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급")
    ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> issueCoupon(
            @PathVariable String couponId,
            @RequestBody CouponV1Dto.IssueRequest request
    );

    @Operation(summary = "내 쿠폰 목록 조회")
    ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> getMyUserCoupons(
            @RequestParam Long memberId
    );
}
