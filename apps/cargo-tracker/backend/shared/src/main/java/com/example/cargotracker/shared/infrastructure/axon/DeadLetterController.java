package com.example.cargotracker.shared.infrastructure.axon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退避したイベントの業務向けの読み口（IT14 レビュー architect・IT15 引き継ぎ 1）。
 *
 * <p><b>運用の道具だけでは足りなかった。</b> 退避先には gulp タスク
 * （{@code projection:dead-letters}）と Actuator（{@code /actuator/deadletters}）から
 * 届くが、<b>どちらも端末からしか触れない</b>。管理者は「投影が止まっている」ことに
 * 画面から気づけず、気づいた人が端末を持っているとは限らなかった。</p>
 *
 * <p><b>中身（payload）は返さない。</b> 退避されたイベントには個人情報が載りうる
 * （[ADR-0003]）。鍵を破棄しても画面に平文が残る形にしない——一覧に要るのは
 * 「どの処理が・どの種類のイベントで・なぜ止まったか」である。</p>
 *
 * <p><b>処理し直す口は 1 つにする。</b> ここは Actuator の入口をそのまま呼ぶ
 * （判定を 2 か所に書かない）。<b>消す手段は置かない</b>——直したあとに退避を消すのは
 * 「黙って捨てる」ことである（ADR-0014 決定 1）。</p>
 *
 * <p><b>経路の接頭辞はサービスごとに違う</b>（{@code /api/v1/booking/…}）ので、
 * {@code cargo.context} から組み立てる。Gateway は接頭辞で振り分けるので、
 * 共有の 1 クラスでも各サービスの経路に載る。</p>
 */
@RestController
@RequestMapping("/api/v1/${cargo.context}/dead-letters")
public class DeadLetterController {

    /**
     * 一覧の上限。<b>全部返さない</b>——退避が積み上がっているときほど、
     * 画面が固まって「何も見えない」になる。
     */
    private static final int LIMIT = 200;

    private static final String SELECT = """
            SELECT dead_letter_id, processing_group, sequence_identifier, event_type,
                   event_identifier, enqueued_at, cause_type, cause_message
              FROM dead_letter_entry
             ORDER BY enqueued_at DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final DeadLetterRetryEndpoint retry;

    public DeadLetterController(JdbcTemplate jdbc, DeadLetterRetryEndpoint retry) {
        this.jdbc = jdbc;
        this.retry = retry;
    }

    /**
     * 退避しているイベント（新しい順）。
     *
     * <p><b>列名を明示して引く。</b> {@code SELECT *} は列の順で組み立てられるので、
     * Axon が列を足したときに全部ずれる。</p>
     */
    @GetMapping
    public Map<String, Object> list() {
        List<DeadLetterView> items = jdbc.query(SELECT,
                (rs, row) -> new DeadLetterView(
                        rs.getString("dead_letter_id"),
                        rs.getString("processing_group"),
                        rs.getString("sequence_identifier"),
                        rs.getString("event_type"),
                        rs.getString("event_identifier"),
                        instantOf(rs.getString("enqueued_at")),
                        rs.getString("cause_type"),
                        rs.getString("cause_message")),
                LIMIT);
        return Map.of("items", items);
    }

    /**
     * 退避したイベントを処理し直す（ADR-0014 決定 1）。
     *
     * <p><b>直っていなければ、また退避される。</b> それが正しい——原因を直さずに
     * 処理し直しても通らないことが、ここで分かる。</p>
     */
    @PostMapping("/retry")
    public Map<String, Object> retry() {
        return retry.retry();
    }

    /**
     * 退避した時刻。<b>Axon は文字列で持つ</b>ので、読むときに変換する。
     *
     * <p>読めない値でも一覧そのものを落とさない——1 行の書式で全体が見えなく
     * なると、止まっていることに気づく手段まで失う。</p>
     */
    private static Instant instantOf(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 退避した 1 件（管理者の一覧）。
     *
     * @param causeMessage なぜ止まったか。<b>これが次の行動を決める</b>
     */
    public record DeadLetterView(
            String deadLetterId,
            String processingGroup,
            String sequenceIdentifier,
            String eventType,
            String eventIdentifier,
            Instant enqueuedAt,
            String causeType,
            String causeMessage) {
    }
}
