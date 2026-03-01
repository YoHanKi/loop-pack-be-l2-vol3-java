package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StockQuantity VO")
class StockQuantityTest {

    @Test
    @DisplayName("유효한 StockQuantity 생성 성공 - 음수 불가")
    void create_valid_stockQuantity() {
        // given & when
        StockQuantity quantity1 = new StockQuantity(0);
        StockQuantity quantity2 = new StockQuantity(100);
        StockQuantity quantity3 = new StockQuantity(999999);

        // then
        assertThat(quantity1.value()).isEqualTo(0);
        assertThat(quantity2.value()).isEqualTo(100);
        assertThat(quantity3.value()).isEqualTo(999999);
    }

    @Test
    @DisplayName("음수이면 예외 발생")
    void negative_stockQuantity_throws_exception() {
        assertThatThrownBy(() -> new StockQuantity(-1))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("재고 수량은 0 이상이어야 합니다");
    }
}
