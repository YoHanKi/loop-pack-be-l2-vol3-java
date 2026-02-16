package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.brand.vo.BrandName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BrandModel Entity")
class BrandModelTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {
        @Test
        @DisplayName("create() 정적 팩토리로 BrandModel 생성 성공")
        void create_brand_model() {
            // given
            String brandId = "nike";
            String brandName = "Nike";

            // when
            BrandModel brand = BrandModel.create(brandId, brandName);

            // then
            assertThat(brand.getBrandId()).isEqualTo(new BrandId(brandId));
            assertThat(brand.getBrandName()).isEqualTo(new BrandName(brandName));
            assertThat(brand.getDeletedAt()).isNull();
            assertThat(brand.isDeleted()).isFalse();
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class Delete {
        @Test
        @DisplayName("markAsDeleted() 호출 시 deletedAt 설정됨")
        void mark_as_deleted_sets_deletedAt() {
            // given
            BrandModel brand = BrandModel.create("adidas", "Adidas");
            assertThat(brand.getDeletedAt()).isNull();

            // when
            ZonedDateTime beforeDelete = ZonedDateTime.now();
            brand.markAsDeleted();
            ZonedDateTime afterDelete = ZonedDateTime.now();

            // then
            assertThat(brand.getDeletedAt()).isNotNull();
            assertThat(brand.getDeletedAt())
                    .isAfterOrEqualTo(beforeDelete)
                    .isBeforeOrEqualTo(afterDelete);
        }

        @Test
        @DisplayName("isDeleted()는 deletedAt이 null이 아니면 true 반환")
        void isDeleted_returns_true_when_deletedAt_is_not_null() {
            // given
            BrandModel brand = BrandModel.create("puma", "Puma");

            // when & then
            assertThat(brand.isDeleted()).isFalse();

            brand.markAsDeleted();
            assertThat(brand.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("markAsDeleted() 중복 호출 시 deletedAt 변경되지 않음 (멱등성)")
        void markAsDeleted_idempotent() {
            // given
            BrandModel brand = BrandModel.create("reebok", "Reebok");
            brand.markAsDeleted();
            ZonedDateTime firstDeletedAt = brand.getDeletedAt();

            // when
            brand.markAsDeleted();

            // then
            assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}
