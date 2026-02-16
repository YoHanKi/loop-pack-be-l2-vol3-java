package com.loopers.domain.brand.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BrandId VO")
class BrandIdTest {

    @Test
    @DisplayName("유효한 BrandId 생성 성공 - 영문+숫자 1-10자")
    void create_valid_brandId() {
        // given & when
        BrandId brandId1 = new BrandId("brand1");
        BrandId brandId2 = new BrandId("BRAND123");
        BrandId brandId3 = new BrandId("b");
        BrandId brandId4 = new BrandId("1234567890");

        // then
        assertThat(brandId1.value()).isEqualTo("brand1");
        assertThat(brandId2.value()).isEqualTo("BRAND123");
        assertThat(brandId3.value()).isEqualTo("b");
        assertThat(brandId4.value()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("null이면 예외 발생")
    void null_brandId_throws_exception() {
        assertThatThrownBy(() -> new BrandId(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("brandId가 비어 있습니다");
    }

    @Test
    @DisplayName("빈 문자열이면 예외 발생")
    void empty_brandId_throws_exception() {
        assertThatThrownBy(() -> new BrandId(""))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("brandId가 비어 있습니다");
    }

    @Test
    @DisplayName("공백 문자열이면 예외 발생")
    void blank_brandId_throws_exception() {
        assertThatThrownBy(() -> new BrandId("   "))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("brandId가 비어 있습니다");
    }

    @Test
    @DisplayName("특수문자 포함 시 예외 발생")
    void brandId_with_special_characters_throws_exception() {
        assertThatThrownBy(() -> new BrandId("brand-1"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~10자");

        assertThatThrownBy(() -> new BrandId("brand_1"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~10자");

        assertThatThrownBy(() -> new BrandId("brand@1"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~10자");
    }

    @Test
    @DisplayName("11자 이상이면 예외 발생")
    void brandId_longer_than_10_throws_exception() {
        assertThatThrownBy(() -> new BrandId("12345678901"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~10자");
    }

    @Test
    @DisplayName("한글 포함 시 예외 발생")
    void brandId_with_korean_throws_exception() {
        assertThatThrownBy(() -> new BrandId("브랜드1"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~10자");
    }
}
