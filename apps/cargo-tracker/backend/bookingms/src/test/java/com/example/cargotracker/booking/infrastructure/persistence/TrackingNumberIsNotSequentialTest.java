package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 追跡番号の連番が戻ってこないことを固定する（ADR-0011 決定 3）。
 *
 * <p>ADR-0010 決定 2 は「シーケンスで採る」と書いていた。訂正は文章だけでは守られない
 * （IT5・IT6 の教訓）。<b>実装が連番に戻ったらここで赤にする。</b></p>
 *
 * <p>採り方の正しさは {@code TrackingNumberGeneratorIT}（実 DB）が見る。こちらは
 * 「連番の道具がソースに残っていないか」だけを見るので、DB を起こさない。</p>
 */
class TrackingNumberIsNotSequentialTest {

    /** モジュールのルート（このテストの作業ディレクトリ）。 */
    private static final Path MAIN = Path.of("src", "main");

    @Test
    @DisplayName("ADR-0011 決定 3: tracking_number_seq を採番に使っている場所が無い")
    void noSourceDrawsFromTheSequence() throws IOException {
        List<Path> drawing = sources()
                .filter(TrackingNumberIsNotSequentialTest::drawsFromTheSequence)
                .toList();

        assertThat(drawing)
                .as("連番だと公開照会（US18）で 1 つ知れば前後が推測できる。"
                        + "採るのは RandomTrackingNumberGenerator（衝突検査つきの乱数）だけ")
                .isEmpty();
    }

    /**
     * シーケンスを<b>読む</b>ところだけを違反とする。
     *
     * <p>落とすマイグレーション（{@code DROP SEQUENCE}）と、なぜやめたかを書いた
     * コメントは残ってよい。理由を消さないと守れない検査は、理由のほうが先に消える。</p>
     */
    private static boolean drawsFromTheSequence(Path source) {
        try {
            return Files.readString(source).contains("nextval('tracking_number_seq')");
        } catch (IOException e) {
            throw new IllegalStateException("読めませんでした: " + source, e);
        }
    }

    private static Stream<Path> sources() throws IOException {
        return Files.walk(MAIN).filter(Files::isRegularFile);
    }
}
