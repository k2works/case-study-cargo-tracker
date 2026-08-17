package com.example.cargotracker.demo;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * デモモードが作ったデータを<strong>まとめて片付ける</strong>。
 *
 * <p><strong>起点は印の付いた荷主だけである</strong>（{@code DemoMark.CONTRACT_PREFIX}）。
 * 品名を起点にすると、<strong>起動時に投入した固定のデータ（マニュアルの図と対応する）を
 * 巻き込む</strong>おそれがある。荷主から辿れば、その荷主の貨物にしか触らない。
 *
 * <p><strong>アプリケーションサービスを通さない理由。</strong> 業務に「予約を消す」操作は
 * 無い（キャンセルはあるが、記録は残る）。<strong>開発環境の後片付けのために
 * 本番の経路へ削除の入口を作るほうが危険</strong>であり、ここだけが直接 SQL を書く。
 *
 * <p><strong>消す順番が制約である。</strong> 親から消すと外部キーに弾かれる。
 * カスケード指定のあるもの（{@code invoice_line_item} / {@code invoice_reminder} /
 * {@code leg} / {@code customs_status_history} / {@code tracking_handling_event} /
 * {@code tracking_exception_event}）は親を消せば付いてくるため、ここには書かない。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoModeCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(DemoModeCleanup.class);

    /** 印の付いた荷主。<strong>すべての削除がここから辿られる。</strong> */
    private static final String MARKED_SHIPPERS =
            "SELECT id FROM shipper WHERE contract_number LIKE ?";

    /** その荷主の貨物の予約 ID。 */
    private static final String MARKED_BOOKINGS =
            "SELECT booking_id FROM cargo WHERE shipper_id IN (" + MARKED_SHIPPERS + ")";

    /**
     * 消す順番。<strong>子から親へ。</strong>
     *
     * <p>それぞれ「印の付いた荷主の予約」に絞り込んでいる。
     */
    private static final List<String> DELETES = List.of(
            // 請求（payment はカスケード指定が無いため明示的に消す）
            "DELETE FROM payment WHERE invoice_id IN ("
                    + "SELECT id FROM invoice WHERE booking_id IN (" + MARKED_BOOKINGS + "))",
            "DELETE FROM invoice WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            // 荷役（通関申告と訂正が荷役を参照している）
            "DELETE FROM customs_declaration WHERE handling_activity_id IN ("
                    + "SELECT id FROM handling_activity WHERE booking_id IN ("
                    + MARKED_BOOKINGS + "))",
            "DELETE FROM handling_correction WHERE handling_activity_id IN ("
                    + "SELECT id FROM handling_activity WHERE booking_id IN ("
                    + MARKED_BOOKINGS + "))",
            "DELETE FROM handling_activity WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            // 追跡（荷役の記録と例外はカスケードで付いてくる）
            "DELETE FROM tracking_activity WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            // 予約に付随するもの
            "DELETE FROM booking_cancellation WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            "DELETE FROM booking_notification WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            // **確定した候補への参照を先に外す。** {@code booking_route_proposal} は
            // 選んだ候補を {@code selected_route_id} で指しており（{@code
            // fk_proposal_selected_route}）、候補から消すと外部キーに弾かれる。
            // 親（提案）を先に消す手もあるが、候補の側が提案を指しているため
            // どちらの順でも片方が残る —— **参照を外してから消す**
            "UPDATE booking_route_proposal SET selected_route_id = NULL"
                    + " WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            "DELETE FROM proposed_route WHERE proposal_id IN ("
                    + "SELECT id FROM booking_route_proposal WHERE booking_id IN ("
                    + MARKED_BOOKINGS + "))",
            "DELETE FROM booking_route_proposal WHERE booking_id IN (" + MARKED_BOOKINGS + ")",
            // 貨物（leg はカスケードで付いてくる）
            "DELETE FROM cargo WHERE shipper_id IN (" + MARKED_SHIPPERS + ")",
            // 荷主
            "DELETE FROM shipper WHERE contract_number LIKE ?",
            // **その貨物のために作った便も消す**（carrier_movement はカスケード）。
            // 便の番号は契約番号と同じ接頭辞を使っており、起動時に投入した
            // 4 便（V0001〜V0004）には当たらない
            "DELETE FROM voyage WHERE voyage_number LIKE ?");

    private final JdbcTemplate jdbcTemplate;

    DemoModeCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 印の付いたデータをすべて消す。
     *
     * <p><strong>ひとつの取引にまとめる。</strong> 途中で失敗して半分だけ消えると、
     * 荷主のいない貨物や請求書のない支払いが残り、画面が壊れる。
     *
     * @return 消した荷主の数
     */
    @Transactional
    int reset() {
        String pattern = DemoMark.CONTRACT_PREFIX + "%";
        int shippers = count(pattern);
        for (String sql : DELETES) {
            jdbcTemplate.update(sql, parameters(sql, pattern));
        }
        LOG.info("デモモードのデータを片付けました（荷主 {} 件）", shippers);
        return shippers;
    }

    /** 片付けの対象になっている荷主の数。<strong>画面に「何件消えるか」を出すために使う。</strong> */
    int pending() {
        return count(DemoMark.CONTRACT_PREFIX + "%");
    }

    private int count(String pattern) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipper WHERE contract_number LIKE ?",
                Integer.class, pattern);
        return count == null ? 0 : count;
    }

    /**
     * プレースホルダの数だけ同じ条件を渡す。
     *
     * <p>入れ子の副問い合わせで {@code ?} が何度も出るため、
     * <strong>数え間違えると引数の数が合わずに落ちる</strong>。文から数える。
     */
    private Object[] parameters(String sql, String pattern) {
        long placeholders = sql.chars().filter(c -> c == '?').count();
        Object[] args = new Object[(int) placeholders];
        java.util.Arrays.fill(args, pattern);
        return args;
    }
}
