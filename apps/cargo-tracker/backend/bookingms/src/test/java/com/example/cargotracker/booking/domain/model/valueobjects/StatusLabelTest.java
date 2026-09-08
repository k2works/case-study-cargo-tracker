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

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("予約の状態の呼び名は正典の要素表が決める（画面と揃っていても正典とずれる）")
    void bookingStatusLabelsMatchTheCanon(BookingStatus status) throws java.io.IOException {
        // **画面と揃っていることだけでは足りない。** 実装と画面を同時に書き写せば
        // 両方ずれたまま緑になる。DELIVERED は正典が「配送完了」と決めているのに
        // 実装は「引取済」で、TransportStatus.DELIVERED と見分けが付かなかった
        // （正典はまさに「文脈語を添えて区別する」と書いている。IT10 で実測）。
        assertThat(status.label())
                .as("%s の呼び名が正典（domain-model.md の状態の一覧）と食い違う", status)
                .isEqualTo(canonLabel(status.name()));
    }

    /** 状態の一覧から `NAME` の行の日本語を読む。 */
    private static String canonLabel(String name) throws java.io.IOException {
        java.nio.file.Path canon = java.nio.file.Path.of("..", "..", "..", "..",
                "docs", "design", "cargo-tracker", "domain-model.md");
        String source = java.nio.file.Files.readString(canon);
        int table = source.indexOf("### 状態の一覧");
        assertThat(table).as("状態の一覧が見つからない").isGreaterThan(-1);
        // BookingStatus の行だけを見る。TransportStatus にも同じコードがある。
        int start = source.indexOf("| 予約状態 `BookingStatus` |", table);
        int end = source.indexOf("| 経路設定状態 `RoutingStatus` |", start);
        assertThat(start).isGreaterThan(-1);
        assertThat(end).isGreaterThan(start);

        for (String line : source.substring(start, end).split("\n")) {
            String[] cells = line.split("\\|");
            if (cells.length > 3 && cells[3].trim().equals("`" + name + "`")) {
                return cells[2].trim();
            }
        }
        throw new AssertionError("正典の状態の一覧に " + name + " が無い");
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
