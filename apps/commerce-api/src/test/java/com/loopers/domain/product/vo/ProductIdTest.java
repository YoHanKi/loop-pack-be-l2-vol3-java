package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductId VO")
class ProductIdTest {

    @Test
    @DisplayName("유효한 ProductId 생성 성공 - 영문+숫자 1-20자")
    void create_valid_productId() {
        // given & when
        ProductId productId1 = new ProductId("prod1");
        ProductId productId2 = new ProductId("PRODUCT123");
        ProductId productId3 = new ProductId("p");
        ProductId productId4 = new ProductId("12345678901234567890");

        // then
        assertThat(productId1.value()).isEqualTo("prod1");
        assertThat(productId2.value()).isEqualTo("PRODUCT123");
        assertThat(productId3.value()).isEqualTo("p");
        assertThat(productId4.value()).isEqualTo("12345678901234567890");
    }

    @Test
    @DisplayName("null이면 예외 발생")
    void null_productId_throws_exception() {
        assertThatThrownBy(() -> new ProductId(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("productId가 비어 있습니다");
    }

    @Test
    @DisplayName("빈 문자열이면 예외 발생")
    void empty_productId_throws_exception() {
        assertThatThrownBy(() -> new ProductId(""))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("productId가 비어 있습니다");
    }

    @Test
    @DisplayName("21자 이상이면 예외 발생")
    void productId_longer_than_20_throws_exception() {
        assertThatThrownBy(() -> new ProductId("123456789012345678901"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~20자");
    }

    @Test
    @DisplayName("특수문자 포함 시 예외 발생")
    void productId_with_special_characters_throws_exception() {
        assertThatThrownBy(() -> new ProductId("prod-1"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문+숫자, 1~20자");
    }
}
