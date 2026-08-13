package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 港マスタが投入されていることを検証する（IT3 タスク 1-1）。
 *
 * <p><strong>マスタが空でも、アプリは何ごともなく起動する。</strong> 気づくのは
 * 航海スケジュールを登録しようとして外部キー違反で落ちたときであり、
 * そのときのエラーは「どの港が足りないのか」を教えてくれない。
 *
 * <p>本テストは業務ルールではなく<strong>前提の存在</strong>を守る。
 * IT3 の計画時、`location` に 1 件もデータが無いことが突合で見つかった。
 */
class LocationMasterTest extends PostgreSQLIntegrationTestBase {

    /** 画面のワイヤーフレーム・動作確認用データ・マニュアルに出てくる港。 */
    private static final List<String> 図とデータに出てくる港 =
            List.of("JPOSA", "USLAX", "JPYOK", "DEHAM", "SGSIN", "JPKIX", "GBFXT");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 港マスタが空でない() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM location", Long.class);

        assertThat(count)
                .as("carrier_movement は location への外部キーを持つ。"
                        + "マスタが空だと航海スケジュールを 1 件も登録できない")
                .isPositive();
    }

    /**
     * 設計ドキュメントと動作確認用データに出てくる港が登録されている。
     *
     * <p>**図に描いた港が実在しないと、図のとおりに操作した人が詰まる。**
     */
    @Test
    void 設計と動作確認に使う港が登録されている() {
        for (String unlocode : 図とデータに出てくる港) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM location WHERE unlocode = ?", Long.class, unlocode);

            assertThat(count).as("%s が港マスタにない", unlocode).isEqualTo(1L);
        }
    }

    /** UN/LOCODE は英大文字 5 文字である。 */
    @Test
    void 港コードはUNLOCODE形式である() {
        List<String> invalid = jdbcTemplate.queryForList(
                "SELECT unlocode FROM location WHERE unlocode !~ '^[A-Z]{2}[A-Z0-9]{3}$'",
                String.class);

        assertThat(invalid).isEmpty();
    }

    /**
     * タイムゾーンが Java の解釈できる名前である。
     *
     * <p>到着期限の判定は日付単位で行うため（`domain-model.md` ビジネスルール 2-1）、
     * **その港の現地時刻に変換できないと日付が 1 日ずれる。**
     * 綴りの誤りは、その港を使う経路が現れるまで発覚しない。
     */
    @Test
    void タイムゾーンはすべて解釈できる() {
        List<String> zones = jdbcTemplate.queryForList(
                "SELECT DISTINCT time_zone FROM location WHERE time_zone IS NOT NULL",
                String.class);

        assertThat(zones).isNotEmpty();
        assertThat(zones).allSatisfy(zone -> assertThat(ZoneId.getAvailableZoneIds())
                .as("%s は解釈できないタイムゾーンである", zone)
                .contains(zone));
    }

    /** すべての港に国コードとタイムゾーンが入っている。 */
    @Test
    void 国コードとタイムゾーンが欠けている港がない() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM location WHERE country_code IS NULL OR time_zone IS NULL",
                Long.class);

        assertThat(count).isZero();
    }
}
