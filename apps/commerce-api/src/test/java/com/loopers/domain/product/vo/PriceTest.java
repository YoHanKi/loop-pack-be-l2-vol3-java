package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Price VO")
class PriceTest {

    @Test
    @DisplayName("유효한 Price 생성 성공 - 음수 불가, scale 2")
    void create_valid_price() {
        // given & when
        Price price1 = new Price(new BigDecimal("1000"));
        Price price2 = new Price(new BigDecimal("0"));
        Price price3 = new Price(new BigDecimal("99999.99"));

        // then
        assertThat(price1.value()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(price2.value()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(price3.value()).isEqualByComparingTo(new BigDecimal("99999.99"));
        assertThat(price1.value().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("null이면 예외 발생")
    void null_price_throws_exception() {
        assertThatThrownBy(() -> new Price(null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("가격이 비어 있습니다");
    }

    @Test
    @DisplayName("음수이면 예외 발생")
    void negative_price_throws_exception() {
        assertThatThrownBy(() -> new Price(new BigDecimal("-1")))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("가격은 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("소수점 3자리 이상은 반올림되어 2자리로 저장됨")
    void price_with_more_than_2_decimals_is_rounded() {
        // given & when
        Price price = new Price(new BigDecimal("1234.567"));

        // then
        assertThat(price.value()).isEqualByComparingTo(new BigDecimal("1234.57"));
        assertThat(price.value().scale()).isEqualTo(2);
    }
}
