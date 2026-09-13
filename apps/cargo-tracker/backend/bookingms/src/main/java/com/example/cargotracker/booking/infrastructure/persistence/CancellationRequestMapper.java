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
            + "ORDER BY requested_at, request_id")
    List<CancellationRequestRow> findPending();

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
