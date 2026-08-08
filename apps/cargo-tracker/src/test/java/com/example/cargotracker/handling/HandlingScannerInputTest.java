package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.interfaces.web.HandlingForm;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * 追跡番号の入力がスキャナに耐えることを確かめる（IT6 レビュー H14）。
 *
 * <p><strong>バーコードスキャナはキーボードとして打ち込む。</strong> 多くの機種は
 * 末尾に改行を送り、機種や設定によっては小文字で送る。前後に空白が混じることもある。
 * 書式の検査（{@code ^TRK-\d{8}-\d{4}$}）はそのどれもを弾く。
 *
 * <p><strong>弾かれた作業員には直しようがない。</strong> 画面には「形式が正しくありません」
 * とだけ出るが、目に見える文字列は正しい。港湾・倉庫ではラベルの汚損・逆光・手袋操作が
 * 常態であり、<strong>スキャン失敗は例外ではなく日常である</strong>（{@code ui_design.md}）。
 *
 * <p>ここで整えるのは<strong>入力の形だけ</strong>である。存在するかどうかの判断は
 * 集約とリポジトリが行う。
 */
@DisplayName("追跡番号のスキャナ入力（H14）")
class HandlingScannerInputTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // 素直な入力はそのまま通る
        "TRK-20260901-0001                  | TRK-20260901-0001",
        // 前後の空白（手入力・コピー貼り付け）
        "'  TRK-20260901-0001  '            | TRK-20260901-0001",
        // 小文字（スキャナの設定・手入力）
        "trk-20260901-0001                  | TRK-20260901-0001",
        // 全角の空白（日本語入力のまま打った）
        "'　TRK-20260901-0001　'            | TRK-20260901-0001",
    })
    void 前後の空白と大小文字を整えて受け取る(String scanned, String expected) {
        var form = new HandlingForm();

        form.setTrackingNumber(scanned);

        assertThat(form.getTrackingNumber()).isEqualTo(expected);
    }

    /**
     * <strong>末尾の改行はスキャナが必ず送る。</strong> これを整えないと、
     * スキャンした入力はすべて書式の検査で弾かれる。
     */
    @ParameterizedTest
    @MethodSource("スキャナが送る終端文字")
    void 末尾の改行やタブを取り除く(String scanned) {
        var form = new HandlingForm();

        form.setTrackingNumber(scanned);

        assertThat(form.getTrackingNumber()).isEqualTo("TRK-20260901-0001");
    }

    private static Stream<String> スキャナが送る終端文字() {
        return Stream.of(
                "TRK-20260901-0001\n",
                "TRK-20260901-0001\r\n",
                "TRK-20260901-0001\t",
                "\nTRK-20260901-0001\n");
    }

    /**
     * <strong>未入力はそのまま未入力として残す。</strong> 空文字を整えて {@code null} に
     * すると、必須の検査が「入力してください」ではなく別のメッセージを出すことがある。
     */
    @ParameterizedTest
    @NullAndEmptySource
    void 未入力はそのまま残す(String scanned) {
        var form = new HandlingForm();

        form.setTrackingNumber(scanned);

        assertThat(form.getTrackingNumber()).isEqualTo(scanned);
    }

    /**
     * <strong>形の整えは書式の検査を置き換えない。</strong> 整えた結果が正しい形に
     * ならないものは、これまでどおり弾かれなければならない。
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "TRK-2026-0001    | TRK-2026-0001",
        "ABC              | ABC",
        "'TRK 20260901 0001' | TRK 20260901 0001",
    })
    void 形の違うものを正しい形に作り変えない(String scanned, String expected) {
        var form = new HandlingForm();

        form.setTrackingNumber(scanned);

        assertThat(form.getTrackingNumber()).isEqualTo(expected);
        // **整えた結果が書式の検査を通ってはならない。** 区切りや桁数を補うと、
        // 誤読した番号が「正しそうな番号」に化けて別の貨物に登録されうる。
        // ここを確かめないと、上のアサートは「変換していない」ことしか言えず、
        // **弾かれ続けること**までは保証しない
        assertThat(form.getTrackingNumber()).doesNotMatch("^TRK-\\d{8}-\\d{4}$");
    }
}
