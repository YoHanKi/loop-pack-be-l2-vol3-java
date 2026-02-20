package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.loopers.interfaces.api.like.LikeV1Dto.*;

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
@RequiredArgsConstructor
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LikeResponse> addLike(
            @PathVariable String productId,
            @Valid @RequestBody AddLikeRequest request
    ) {
        var info = likeFacade.addLike(request.memberId(), productId);
        return ApiResponse.success(LikeResponse.from(info));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeLike(
            @PathVariable String productId,
            @Valid @RequestBody RemoveLikeRequest request
    ) {
        likeFacade.removeLike(request.memberId(), productId);
        return ApiResponse.success(null);
    }
}
