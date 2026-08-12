package com.example.cargotracker.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 動作確認用データを投入済みかを判定する。
 *
 * <p><strong>印にできるのは、投入する側しか作らないものだけである</strong>（IT19 で 3 回間違えた）。
 *
 * <ul>
 *   <li>「見積が 1 件でもあれば投入済み」——<strong>見積を作る別のテストが先に走ると、
 *       投入が丸ごと飛んで章が空のまま緑になった</strong></li>
 *   <li>「動作確認用の荷主が居れば投入済み」——<strong>その荷主は {@code db/demo} の
 *       {@code V900} が SQL で作る</strong>。local を起動すると必ず先に居るため、
 *       投入が一度も走らなかった</li>
 *   <li>「その荷主に予約があれば投入済み」——<strong>予約も {@code V901} が SQL で作る</strong>。
 *       同じ理由で走らなかった</li>
 * </ul>
 *
 * <p>そこで<strong>ここでしか書かない品名</strong>を印にする。{@code db/demo} の SQL は
 * 別の品名を使っており、業務の利用者がこの文字列を打ち込むことも実際上ない。
 *
 * <p><strong>マッパーを介さず直接数える。</strong> 品名で貨物を引く読み取りは業務に無く、
 * 印のためだけに読み取りの入口を増やすと、<strong>本番の経路が動作確認用データを
 * 知ることになる</strong>。
 */
@Component
class DemoInstallMarker {

    /** <strong>ここでしか書かない品名。</strong> 投入した貨物の目印である。 */
    static final String MARKER_DESCRIPTION = "動作確認用の貨物";

    private final JdbcTemplate jdbcTemplate;

    DemoInstallMarker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean alreadyInstalled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cargo WHERE description = ?",
                Integer.class, MARKER_DESCRIPTION);
        return count != null && count > 0;
    }
}
