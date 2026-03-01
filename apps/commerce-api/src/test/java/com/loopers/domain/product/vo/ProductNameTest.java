package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductName VO")
class ProductNameTest {

    @Test
    @DisplayName("유효한 ProductName 생성 성공 - 1-100자")
    void create_valid_productName() {
        // given & when
        ProductName productName1 = new ProductName("Nike Air Max");
        ProductName productName2 = new ProductName("갤럭시 S24");
        ProductName productName3 = new ProductName("A");
        ProductName productName4 = new ProductName("A".repeat(100));

        // then
        assertThat(productName1.value()).isEqualTo("Nike Air Max");
        assertThat(productName2.value()).isEqualTo("갤럭시 S24");
        assertThat(productName3.value()).isEqualTo("A");
        assertThat(productName4.value()).hasSize(100);
    }

    @Test
    @DisplayName("null이면 예외 발생")
    void null_productName_throws_exception() {
        assertThatThrownBy(() -> new ProductName(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명이 비어 있습니다");
    }

    @Test
    @DisplayName("빈 문자열이면 예외 발생")
    void empty_productName_throws_exception() {
        assertThatThrownBy(() -> new ProductName(""))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명이 비어 있습니다");
    }

    @Test
    @DisplayName("101자 이상이면 예외 발생")
    void productName_longer_than_100_throws_exception() {
        String longName = "A".repeat(101);
        assertThatThrownBy(() -> new ProductName(longName))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명 길이는 1자 이상 100자 이하여야 합니다");
    }

    @Test
    @DisplayName("앞뒤 공백은 trim 처리됨")
    void productName_with_leading_trailing_spaces_is_trimmed() {
        // given & when
        ProductName productName = new ProductName("  Nike Air  ");

        // then
        assertThat(productName.value()).isEqualTo("Nike Air");
    }
}
