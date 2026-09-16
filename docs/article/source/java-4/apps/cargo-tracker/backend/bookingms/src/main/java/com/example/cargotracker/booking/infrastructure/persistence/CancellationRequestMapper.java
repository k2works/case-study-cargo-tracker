package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * キャンセル申請の投影（UC22 / US30。IT15 T3）。
 *
 * <p><b>承認待ちの一覧（S23）と、予約詳細の履歴（S22）が読む。</b> 集約が申請を
 * 持っているのは「二重に申請させない」ためで、人が読む口はここである。</p>
 *
 * <p><b>列名を明示して引く。</b> {@code SELECT *} は列の順で組み立てられるので、
 * 列を足すたびに項目が丸ごとずれる。</p>
 */
@Mapper
public interface CancellationRequestMapper {

    /** 申請を記録する。**二度届いても 1 行**（`request_id` が PK）。 */
    int insert(CancellationRequestRow row);

    /**
     * 判断を書く。
     *
     * <p><b>承認待ちの行にだけ書く。</b> 条件を SQL に置かないと、先に読んでから
     * 更新するあいだに他の人が判断した跡を上書きできてしまう。</p>
     */
    int decide(@Param("requestId") String requestId,
            @Param("decision") String decision,
            @Param("dischargeUnLocode") String dischargeUnLocode,
            @Param("decisionReason") String decisionReason,
            @Param("decidedBy") String decidedBy,
            @Param("decidedAt") Instant decidedAt,
            @Param("projectedAt") Instant projectedAt);

    /**
     * 承認待ちの申請（S23）。<b>申請日時が古い順</b>——待たせているものから答える。
     *
     * <p><b>NULL が承認待ち。</b> 別の列を置くと、同じ事実を 2 か所が持つ。</p>
     */
    @Select("SELECT request_id, booking_id, reason, requested_by, requested_at, "
            + "decision, discharge_unlocode, decision_reason, decided_by, decided_at "
            + "FROM cancellation_request WHERE decision IS NULL "
            // **シミュレーション由来は外す**（[ADR-0020] 決定 4）。US36 の継続実行は
            // 輸送中キャンセルのシナリオを流し続けるので、承認待ちが偽物で埋まる
            // ——陸揚げ地を決められるのは追跡管理者だけで、毎朝ここを開く
            // （IT17 のレビューで指摘）。
            // **「写しがある」ことを条件にしない。** 投影が届く前の一瞬、本物の
            // 申請まで消える（handlingms の同じ形をフルビルドで実測）。
            // **外すのは「偽物だと分かっているもの」だけ**にする。
            + "  AND NOT EXISTS (SELECT 1 FROM cargo_summary c "
            + "                  WHERE c.booking_id = cancellation_request.booking_id "
            + "                    AND c.simulated = TRUE) "
            + "ORDER BY requested_at, request_id")
    List<CancellationRequestRow> findPending();

    /**
     * 自分が申請して<b>却下された</b>もの（S02 営業。US30 §受入基準 7 の落とし先）。
     *
     * <p><b>却下だけを出す。</b> 承認されれば予約が「キャンセル」になり、予約一覧に
     * そのまま出る——気づける。却下は<b>何も変わらない</b>ので、申請した本人が
     * 予約詳細を開き直さない限り誰も気づかない。</p>
     *
     * <p><b>既読は持たない。</b> 読んだかどうかを覚える表を足すより、期間で区切る
     * ほうが安い（{@code since} 以降に判断されたもの）。営業の「最近のできごと」で
     * あって、未処理の待ち行列ではない。</p>
     *
     * <p><b>新しい順</b>——いま何が起きたかが先に読める。</p>
     */
    @Select("SELECT request_id, booking_id, reason, requested_by, requested_at, "
            + "decision, discharge_unlocode, decision_reason, decided_by, decided_at "
            + "FROM cancellation_request "
            + "WHERE decision = 'REJECTED' AND requested_by = #{requestedBy} "
            + "  AND decided_at >= #{since} "
            + "ORDER BY decided_at DESC, request_id "
            + "LIMIT #{limit}")
    List<CancellationRequestRow> findRecentlyRejected(
            @Param("requestedBy") String requestedBy,
            @Param("since") Instant since,
            @Param("limit") int limit);

    /** その予約の申請（S22 の履歴）。<b>新しい順</b>——いま何が起きているかが先に読める。 */
    @Select("SELECT request_id, booking_id, reason, requested_by, requested_at, "
            + "decision, discharge_unlocode, decision_reason, decided_by, decided_at "
            + "FROM cancellation_request WHERE booking_id = #{bookingId} "
            + "ORDER BY requested_at DESC, request_id")
    List<CancellationRequestRow> findByBooking(@Param("bookingId") String bookingId);

    /**
     * 申請の 1 行。
     *
     * @param decision {@code APPROVED} / {@code REJECTED} / {@code null}（承認待ち）
     */
    record CancellationRequestRow(
            String requestId,
            String bookingId,
            String reason,
            String requestedBy,
            Instant requestedAt,
            String decision,
            String dischargeUnLocode,
            String decisionReason,
            String decidedBy,
            Instant decidedAt) {
    }
}
