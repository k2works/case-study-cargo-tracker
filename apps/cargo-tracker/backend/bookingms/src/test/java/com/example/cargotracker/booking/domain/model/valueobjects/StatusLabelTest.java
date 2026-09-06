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
    @DisplayName("経路設定状態の呼び名が画面と食い違わない（マニュアルが「同じ呼び名」と保証している）")
    void routingStatusLabelsMatchTheScreen() throws Exception {
        // マニュアル 09 章は「断りの括弧の中は、予約詳細の『経路設定状態』と同じ
        // 呼び名」と書いている。片方だけ直すとその保証が黙って崩れる（IT6 レビュー 中）。
        String api = screenApi();
        for (RoutingStatus status : RoutingStatus.values()) {
            assertThat(api)
                    .as("%s の呼び名が画面と食い違う", status)
                    .contains(status.name() + ": '" + status.label() + "'");
        }
    }

    @Test
    @DisplayName("予約の状態の呼び名が画面と食い違わない")
    void bookingStatusLabelsMatchTheScreen() throws Exception {
        // **経路設定状態にだけ置いていた検査を広げる**（IT6 引き継ぎ 3）。
        // 予約の状態は一覧・詳細・ダッシュボードの全部に出るので、食い違いは
        // 経路設定状態より広く見える。それでも固定されていなかった。
        String api = screenApi();
        for (BookingStatus status : BookingStatus.values()) {
            assertThat(api)
                    .as("%s の呼び名が画面と食い違う", status)
                    .contains(status.name() + ": '" + status.label() + "'");
        }
    }

    /** 画面の呼び名表。実行時のカレントは bookingms で、フロントは backend の 1 つ上。 */
    private static String screenApi() throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(
                "../../frontend/src/features/bookings/api.ts"));
    }
}
