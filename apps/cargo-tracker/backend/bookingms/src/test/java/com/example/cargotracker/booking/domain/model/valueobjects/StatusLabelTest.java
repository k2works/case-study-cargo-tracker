package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("画面の呼び名と食い違わない（マニュアルが「同じ呼び名」と保証している）")
    void labelsMatchTheScreen() throws Exception {
        // マニュアル 09 章は「断りの括弧の中は、予約詳細の『経路設定状態』と同じ
        // 呼び名」と書いている。片方だけ直すとその保証が黙って崩れる（IT6 レビュー 中）。
        // 実行時のカレントは bookingms。フロントは backend の 1 つ上にある。
        String api = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../../frontend/src/features/bookings/api.ts"));
        for (RoutingStatus status : RoutingStatus.values()) {
            assertThat(api)
                    .as("%s の呼び名が画面と食い違う", status)
                    .contains(status.name() + ": '" + status.label() + "'");
        }
    }
}
