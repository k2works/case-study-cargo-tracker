package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.port.TrackingNumberGenerator;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 追跡番号の形式（domain-model.md:1431 / ADR-0011）。
 *
 * <p><b>荷主に共有され、認証なしで照会できる</b>（US18 の公開照会）。連番だと
 * 1 つ知れば前後がすべて推測でき、他人の貨物の状態・現在地・到着予定が読める。
 * `ui_design.md` の「総当たり対策：推測しにくい形式」はこの検査で守る。</p>
 *
 * <p>IT7 の実装は {@code T-2026-000001} の連番だった（IT8 の着手前検証で発見。
 * クラスタの実データが `-000001`・`-000005`・`-000007` と並んでいた）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingNumberGeneratorIT extends AbstractAxonIntegrationTest {

    /** 正典（domain-model.md:1431）: `TRK-` + 大文字英数字 10 桁。 */
    private static final String CANON = "^TRK-[0-9A-Z]{10}$";

    @Autowired
    private TrackingNumberGenerator generator;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("ADR-0011 決定 1: 正典の形式で採る（TRK- + 大文字英数字 10 桁）")
    void followsTheCanonicalFormat() {
        assertThat(generator.next()).matches(CANON);
    }

    @Test
    @DisplayName("ADR-0011 決定 2: 連番でない（1 つ知っても次が推測できない）")
    void isNotSequential() {
        // **連番だと公開照会で他人の貨物が読める。** 隣り合う番号の距離を見る。
        List<String> numbers = IntStream.range(0, 20).mapToObj(i -> generator.next()).toList();

        long adjacent = IntStream.range(1, numbers.size())
                .filter(i -> differsByOneCharacter(numbers.get(i - 1), numbers.get(i)))
                .count();

        assertThat(adjacent)
                .as("隣が 1 文字違いで並ぶなら、1 つ知れば総当たりが要らない")
                .isZero();
    }

    /** 末尾 1 文字だけが違うか（連番の形）。 */
    private static boolean differsByOneCharacter(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                diff++;
            }
        }
        return diff == 1;
    }

    @Test
    @DisplayName("重ならない（同じ番号が 2 つ出ると、別の貨物が同じ追跡に見える）")
    void doesNotCollide() {
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            assertThat(seen.add(generator.next()))
                    .as("同じ番号が 2 回出た")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("ADR-0011 決定 4: 連番のシーケンスは残っていない（既存番号は開発データを作り直す）")
    void theSequenceIsGone() {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_class WHERE relkind = 'S' "
                        + "AND relname = 'tracking_number_seq')", Boolean.class);

        assertThat(exists)
                .as("V016 で落とす。残っていると連番の採り方へ戻れてしまう")
                .isFalse();
    }
}
