package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.attention.AttentionItemId;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * マイグレーション（{@code V005}）の導出が、Java の導出と一致する。
 *
 * <p><b>導出は 2 か所にある。</b> ひとつは {@link AttentionItemId}、もうひとつは既存行を
 * 付け替える SQL。IT4 の R.1・R.2 は「導出が 2 本あって食い違った」ことへの返済だったので、
 * ここで一致を固定しないと同じ形が戻る。</p>
 *
 * <p>ずれても CI は全緑のまま、<b>既存クラスタの行だけ</b>が新しい導出と食い違い、
 * 同じ事実が 2 行に並ぶ。区切り文字や桁数を変えたときに、ここが赤になる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttentionItemIdMigrationIT extends AbstractAxonIntegrationTest {

    /** V005 の UPDATE と同じ式。マイグレーションから写して比べる形にはしない。 */
    private static final String DERIVATION = """
            SELECT substr(
                encode(
                    sha256(convert_to(
                        ? || chr(31) || ? || chr(31) || ? || chr(31) || ?,
                        'UTF8')),
                    'hex'),
                1, 32)
            """;

    /** 発生時刻は固定する。テストが実時計を読むと、実行した時刻で結果が変わる。 */
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-05T00:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("SQL の導出と Java の導出は同じ値になる")
    void sqlDerivationMatchesJava() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<String[]> facts = List.of(
                new String[] {"PROJECTION_REJECTED", "SHIPPER", "SHP-1", "メールアドレスの重複"},
                new String[] {"PROJECTION_REJECTED", "VOYAGE", "V-MOL-001", "航海番号の重複"},
                new String[] {"REACTION_FAILED", "BOOKING", "B-1", "修正の対象が投影に無い"});

        for (String[] fact : facts) {
            String fromSql = jdbc.queryForObject(DERIVATION, String.class,
                    (Object[]) fact);

            assertThat(fromSql)
                    .as("マイグレーションと共有カーネルの導出がずれると、"
                            + "既存クラスタの行だけが新しい導出と食い違う")
                    .isEqualTo(AttentionItemId.of(fact[0], fact[1], fact[2], fact[3]).value());
        }
    }

    @Test
    @DisplayName("マイグレーション後の行は、新しい導出で読み直せる")
    void migratedRowsAreAddressableByTheNewDerivation() {
        // 実際に流れた V005 の結果を見る。式だけを比べると、マイグレーションが
        // 流れていなくても緑になる。
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String kind = "PROJECTION_REJECTED";
        String targetId = "SHP-MIG-" + System.nanoTime();
        String reason = "メールアドレスの重複";
        String expected = AttentionItemId.of(kind, "SHIPPER", targetId, reason).value();

        jdbc.update("INSERT INTO attention_item (item_id, kind, target_type, target_id, "
                + "assigned_role, reason, payload, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?)",
                expected, kind, "SHIPPER", targetId, "ROLE_SALES", reason,
                java.sql.Timestamp.from(OCCURRED_AT));

        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM attention_item WHERE item_id = ?", Integer.class, expected);

        assertThat(found).isEqualTo(1);
    }
}
