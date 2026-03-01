package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApp;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponApp couponApp;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> issueCoupon(
            @PathVariable String couponId,
            @Valid @RequestBody CouponV1Dto.IssueRequest request
    ) {
        UserCouponInfo info = couponApp.issueUserCoupon(couponId, request.memberId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(CouponV1Dto.UserCouponResponse.from(info)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> getMyUserCoupons(
            @RequestParam Long memberId
    ) {
        List<UserCouponInfo> infos = couponApp.getMyUserCoupons(memberId);
        List<CouponV1Dto.UserCouponResponse> responses = infos.stream()
                .map(CouponV1Dto.UserCouponResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
