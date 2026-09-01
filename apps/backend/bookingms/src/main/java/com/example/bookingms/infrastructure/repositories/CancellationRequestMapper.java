package com.example.bookingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CancellationRequestMapper {

    String COLUMNS = """
            id, cargo_id, reason, status, requested_by, requested_at,
            booking_status_at_request, discharge_location_unlocode,
            decided_by, decided_at, decision_reason
            """;

    /**
     * 荷主で絞る問い合わせ用の列（{@code cr} で修飾する）。
     *
     * <p><strong>修飾なしでは曖昧になる。</strong>{@code cargo} も {@code shipper} も
     * {@code id} を持つため、結合した瞬間にどちらの {@code id} か決まらない。
     */
    String CR_COLUMNS = """
            cr.id, cr.cargo_id, cr.reason, cr.status, cr.requested_by, cr.requested_at,
            cr.booking_status_at_request, cr.discharge_location_unlocode,
            cr.decided_by, cr.decided_at, cr.decision_reason
            """;

    @Insert("""
            INSERT INTO cancellation_request (
                cargo_id, reason, status, requested_by, requested_at,
                booking_status_at_request, discharge_location_unlocode,
                decided_by, decided_at, decision_reason)
            VALUES (
                #{cargoId}, #{reason}, #{status}, #{requestedBy}, #{requestedAt},
                #{bookingStatusAtRequest}, #{dischargeLocationUnlocode},
                #{decidedBy}, #{decidedAt}, #{decisionReason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(CancellationRequestRecord row);

    /** **常に INSERT する save にしない。**承認・却下はこちらで書く。 */
    @Update("""
            UPDATE cancellation_request
               SET status = #{status},
                   discharge_location_unlocode = #{dischargeLocationUnlocode},
                   decided_by = #{decidedBy},
                   decided_at = #{decidedAt},
                   decision_reason = #{decisionReason},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    void updateDecision(CancellationRequestRecord row);

    @Select("SELECT " + COLUMNS + " FROM cancellation_request WHERE id = #{id}")
    @Results(id = "cancellationResult", value = {
        @Result(column = "cargo_id", property = "cargoId"),
        @Result(column = "requested_by", property = "requestedBy"),
        @Result(column = "requested_at", property = "requestedAt"),
        @Result(column = "booking_status_at_request", property = "bookingStatusAtRequest"),
        @Result(column = "discharge_location_unlocode", property = "dischargeLocationUnlocode"),
        @Result(column = "decided_by", property = "decidedBy"),
        @Result(column = "decided_at", property = "decidedAt"),
        @Result(column = "decision_reason", property = "decisionReason"),
    })
    CancellationRequestRecord findById(@Param("id") long id);

    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM cancellation_request
             WHERE cargo_id = #{cargoId}
               AND status = 'REQUESTED'
             ORDER BY requested_at DESC, id DESC
             LIMIT 1
            """)
    @ResultMap("cancellationResult")
    CancellationRequestRecord findAwaitingByCargoId(@Param("cargoId") long cargoId);

    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM cancellation_request
             WHERE cargo_id = #{cargoId}
             ORDER BY requested_at DESC, id DESC
             LIMIT 1
            """)
    @ResultMap("cancellationResult")
    CancellationRequestRecord findLatestByCargoId(@Param("cargoId") long cargoId);

    /**
     * その貨物のキャンセル申請を<strong>すべて</strong>返す（US30-10）。
     *
     * <p><strong>最新の 1 件では足りない。</strong>却下されて再申請すると、前回の却下理由が
     * 予約詳細から消える——「なぜ一度断られたか」は、次に荷主と話す営業がいちばん必要と
     * する情報である。
     *
     * <p><strong>新しい順</strong>に返す。いま何が起きているかが先に来る。
     */
    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM cancellation_request
             WHERE cargo_id = #{cargoId}
             ORDER BY requested_at DESC, id DESC
            """)
    @ResultMap("cancellationResult")
    java.util.List<CancellationRequestRecord> findAllByCargoId(@Param("cargoId") long cargoId);

    /** 承認待ちの一覧（US30-4）。**古い順**——放っておくほど貨物は目的地へ近づく。 */
    @Select("""
            SELECT
            """ + CR_COLUMNS + """
              FROM cancellation_request cr
              JOIN cargo c ON c.id = cr.cargo_id
              JOIN shipper s ON s.id = c.shipper_id
             WHERE cr.status = 'REQUESTED'
               -- **架空の申請を出さない**（TD-02・IT16）。承認するのは追跡管理者で
               -- あり、この一覧が唯一の入口である——毎朝ここから今日やることを決める
               AND
               """ + SimulatedShipperFilter.EXCLUDE_SIMULATED + """
             ORDER BY cr.requested_at, cr.id
             LIMIT #{limit}
            """)
    @ResultMap("cancellationResult")
    List<CancellationRequestRecord> findAwaitingDecision(@Param("limit") int limit);

    /**
     * <strong>陸揚げ待ち</strong>——承認済みで陸揚げ地が決まっている申請（IT10 返済枠 0.3）。
     *
     * <p><strong>荷役の担当者には、陸揚げ地が決まったことを知る入口が無かった。</strong>
     * 作業指示は自動で作られず（[ADR-025] 決定 5）、承認した追跡管理者からの連絡が唯一の
     * 担保である——<strong>連絡を忘れると、貨物は指定した港を通り過ぎる</strong>。
     *
     * <p><strong>古い順</strong>。承認から時間が経つほど、船は港に近づく。
     */
    @Select("""
            SELECT
            """ + CR_COLUMNS + """
              FROM cancellation_request cr
              JOIN cargo c ON c.id = cr.cargo_id
              JOIN shipper s ON s.id = c.shipper_id
             WHERE cr.status = 'APPROVED'
               AND cr.discharge_location_unlocode IS NOT NULL
               -- **架空の案件を出さない**（TD-02・IT16）。荷役作業員はここで自分の
               -- 手番に気づく——混ざると、実在の貨物が指定した港を通り過ぎる
               AND
               """ + SimulatedShipperFilter.EXCLUDE_SIMULATED + """
             ORDER BY cr.decided_at, cr.id
             LIMIT #{limit}
            """)
    @ResultMap("cancellationResult")
    List<CancellationRequestRecord> findAwaitingDischarge(@Param("limit") int limit);
}
