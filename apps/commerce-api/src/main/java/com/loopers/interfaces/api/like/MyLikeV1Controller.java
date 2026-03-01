package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikedProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/likes")
@RequiredArgsConstructor
public class MyLikeV1Controller {

    private final LikeFacade likeFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> getMyLikedProducts(
            @RequestParam Long memberId,
            Pageable pageable
    ) {
        Page<LikedProductInfo> likes = likeFacade.getMyLikedProducts(memberId, pageable);
        List<LikeV1Dto.LikedProductResponse> response = likes.getContent().stream()
                .map(LikeV1Dto.LikedProductResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
