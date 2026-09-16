package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ウォーキングスケルトンの土台が実環境と同じ形で立ち上がることを固定する。
 *
 * <p>ここまでに 3 回、「壊れているのに起動が成功する」失敗に当たっている
 * （connector の版ずれ・MyBatis の版ずれ・Flyway の自動設定欠落）。いずれも
 * 単体テストでは見えず、実際に立ち上げて初めて分かった。だから統合テストで固定する。</p>
 */
@SpringBootTest
// クラスが終わったらコンテキストを閉じる。閉じないと複数のコンテキストが同時に
// 生きたまま同じ Axon Server にハンドラを登録し、二重登録で起動に失敗する
// （DuplicateQueryHandlerSubscriptionException）。落ちるテストが実行順で変わる。
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingBootstrapIT extends AbstractAxonIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Flyway が適用され token_entry がある")
    void migrationsAreApplied() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tokenEntry = jdbc.queryForObject(
                // スキーマはテストクラスごとに分けているので、現在のスキーマに限る。
                // 限らないと他のクラスが作った表まで数えて、増えるたびに落ちる。
                "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = current_schema() AND table_name = 'token_entry'",
                Integer.class);
        assertThat(tokenEntry)
                .as("マイグレーションが 1 本も走らないまま起動が成功したことが実際にあった")
                .isEqualTo(1);

        Integer mask = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_schema = current_schema() AND table_name = 'token_entry'"
                        + " AND column_name = 'mask' AND is_nullable = 'NO'",
                Integer.class);
        assertThat(mask).as("token_entry.mask は NOT NULL。無いと起動時に落ちる").isEqualTo(1);
    }

    @Test
    @DisplayName("起動時接続検査を通っている（DCB 有効の Axon Server に繋がっている）")
    void startupCheckPassed() {
        // 検査は ApplicationRunner なので、ここに来ている時点で通っている。
        // 通らなければコンテキストの生成が失敗し、このテスト自体が動かない。
        assertThat(dataSource).isNotNull();
    }
}
