package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 状態の呼び名は、値の一覧から回して確かめる。
 *
 * <p>値を足したときに扱っていない場所は名乗り出ない。1 件ずつ書くと、
 * 足した値のラベルだけが抜けたまま緑になる。</p>
 */
class StatusLabelTest {

    @ParameterizedTest
    @EnumSource(RoutingStatus.class)
    @DisplayName("経路設定状態は列挙名でない日本語の呼び名を持つ")
    void routingStatusHasJapaneseLabel(RoutingStatus status) {
        assertThat(status.label()).isNotBlank().isNotEqualTo(status.name());
        assertThat(status.label()).doesNotMatch("^[A-Z_]+$");
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("予約の状態は列挙名でない日本語の呼び名を持つ")
    void bookingStatusHasJapaneseLabel(BookingStatus status) {
        assertThat(status.label()).isNotBlank().isNotEqualTo(status.name());
        assertThat(status.label()).doesNotMatch("^[A-Z_]+$");
    }
}
