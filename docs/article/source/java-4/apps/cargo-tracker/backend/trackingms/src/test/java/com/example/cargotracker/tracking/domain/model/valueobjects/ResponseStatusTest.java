package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 例外対応状態（domain-model.md の要素表）。 */
class ResponseStatusTest {

    @Test
    @DisplayName("3 値がそろっている")
    void hasThreeValues() {
        assertThat(ResponseStatus.values())
                .containsExactly(ResponseStatus.REPORTED, ResponseStatus.RESPONDING,
                        ResponseStatus.RESOLVED);
    }

    @ParameterizedTest
    @EnumSource(ResponseStatus.class)
    @DisplayName("呼び名は要素表が正典（利用者に列挙名を見せない）")
    void usesTheCanonicalLabel(ResponseStatus status) throws IOException {
        assertThat(status.label())
                .isEqualTo(ExceptionTypeTest.canonLabels("例外対応状態 `ResponseStatus`")
                        .get(status.name()));
    }

    @Test
    @DisplayName("解決済みだけが決着している（一覧が既定で外す）")
    void onlyResolvedIsSettled() {
        assertThat(ResponseStatus.REPORTED.settled()).isFalse();
        assertThat(ResponseStatus.RESPONDING.settled()).isFalse();
        assertThat(ResponseStatus.RESOLVED.settled()).isTrue();
    }
}
