package com.loopers.domain.like;

import com.loopers.domain.like.vo.RefMemberId;
import com.loopers.domain.like.vo.RefProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LikeModel Entity")
class LikeModelTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {

        @Test
        @DisplayName("create() 정적 팩토리로 LikeModel 생성 성공")
        void create_like_model() {
            // given
            Long refMemberId = 1L;
            Long refProductId = 100L;

            // when
            LikeModel like = LikeModel.create(refMemberId, refProductId);

            // then
            assertThat(like).isNotNull();
            assertThat(like.getRefMemberId()).isEqualTo(new RefMemberId(refMemberId));
            assertThat(like.getRefProductId()).isEqualTo(new RefProductId(refProductId));
        }

        @Test
        @DisplayName("Member PK와 Product PK로 좋아요 생성")
        void create_withMemberAndProductIds() {
            // given
            Long refMemberId = 5L;
            Long refProductId = 200L;

            // when
            LikeModel like = LikeModel.create(refMemberId, refProductId);

            // then
            assertThat(like.getRefMemberId().value()).isEqualTo(5L);
            assertThat(like.getRefProductId().value()).isEqualTo(200L);
        }
    }
}
