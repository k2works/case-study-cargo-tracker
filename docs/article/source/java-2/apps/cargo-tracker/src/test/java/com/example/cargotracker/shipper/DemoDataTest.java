package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 動作確認用データ（{@code db/demo}）の適用範囲を検証する。
 *
 * <p><strong>動作確認用データが本番のマイグレーションに混ざってはならない。</strong>
 * {@code db/demo} は local / dev プロファイルの {@code spring.flyway.locations} に
 * 明示的に追加した場合にのみ適用される。既定の locations には含めない。
 *
 * <p><strong>行の有無では判定しない</strong>（IT19）。以前は
 * {@code shipper-sample@example.com} の荷主が居ないことを確かめていたが、
 * <strong>同じ荷主をアプリケーションサービス経由で作るテストが増えた瞬間に落ちた</strong>
 * （{@code DemoDataInstallerTest}）。落ちた理由は「本番に動作確認用データが混ざった」
 * ことではなく、<strong>別のテストが同じ行を正規の経路で作った</strong>ことである。
 *
 * <p>そのため<strong>適用されたマイグレーションそのもの</strong>を見る。
 * {@code db/demo} のバージョンは 900 番台であり、これが履歴に載っていれば
 * 「本番にも入る経路がある」ことを意味する。
 */
class DemoDataTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void 既定のマイグレーションに動作確認用データは含まれない() {
        List<String> applied = new JdbcTemplate(dataSource).queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL",
                String.class);

        assertThat(applied)
                .as("適用されたマイグレーション")
                .isNotEmpty()
                // test プロファイルは db/seed（800 番台の利用者）まで適用する。
                // **業務データ（db/demo。900 番台）は入れない**
                .allSatisfy(version -> assertThat(Integer.parseInt(version))
                        .as("動作確認用の業務データ（900 番台）が既定の locations に入っている")
                        .isLessThan(900));
    }
}
