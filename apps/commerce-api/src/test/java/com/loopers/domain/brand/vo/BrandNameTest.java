package com.loopers.domain.brand.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BrandName VO")
class BrandNameTest {

    @Test
    @DisplayName("유효한 BrandName 생성 성공 - 1-50자")
    void create_valid_brandName() {
        // given & when
        BrandName brandName1 = new BrandName("Nike");
        BrandName brandName2 = new BrandName("삼성전자");
        BrandName brandName3 = new BrandName("A");
        BrandName brandName4 = new BrandName("A".repeat(50));

        // then
        assertThat(brandName1.value()).isEqualTo("Nike");
        assertThat(brandName2.value()).isEqualTo("삼성전자");
        assertThat(brandName3.value()).isEqualTo("A");
        assertThat(brandName4.value()).hasSize(50);
    }

    @Test
    @DisplayName("null이면 예외 발생")
    void null_brandName_throws_exception() {
        assertThatThrownBy(() -> new BrandName(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명이 비어 있습니다");
    }

    @Test
    @DisplayName("빈 문자열이면 예외 발생")
    void empty_brandName_throws_exception() {
        assertThatThrownBy(() -> new BrandName(""))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명이 비어 있습니다");
    }

    @Test
    @DisplayName("공백 문자열이면 예외 발생")
    void blank_brandName_throws_exception() {
        assertThatThrownBy(() -> new BrandName("   "))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명이 비어 있습니다");
    }

    @Test
    @DisplayName("51자 이상이면 예외 발생")
    void brandName_longer_than_50_throws_exception() {
        String longName = "A".repeat(51);
        assertThatThrownBy(() -> new BrandName(longName))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명 길이는 1자 이상 50자 이하여야 합니다");
    }

    @Test
    @DisplayName("앞뒤 공백은 trim 처리됨")
    void brandName_with_leading_trailing_spaces_is_trimmed() {
        // given & when
        BrandName brandName = new BrandName("  Nike  ");

        // then
        assertThat(brandName.value()).isEqualTo("Nike");
    }
}
