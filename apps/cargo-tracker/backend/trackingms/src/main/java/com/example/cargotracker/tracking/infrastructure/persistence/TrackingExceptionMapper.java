package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 輸送中の例外（US19 / UC16）。正典は data-model.md「tracking_exception」。 */
@Mapper
public interface TrackingExceptionMapper {

    /**
     * 読み出す列を並べる。<b>{@code SELECT *} にしない</b>——注釈マッパーは
     * record に<b>列順で</b>割り当てるので、列を足した瞬間に全部ずれる。
     */
    String COLUMNS = "exception_id, tracking_number, exception_type, response_status, "
            + "urgent, unlocode, description, resolution, new_estimated_arrival, "
            + "response_plan, occurred_at, resolved_at, projected_at";

    /**
     * 起票を 1 行足す。
     *
     * <p><b>リプレイで増えない。</b> 主キーは例外の識別子で、再配送も読み直しも
     * 同じ行に落ちる。</p>
     */
    int insert(TrackingExceptionRow row);

    /**
     * 対応を始めた。<b>起票の内容は書き換えない</b>（追記のみ・不変条件 6）。
     *
     * <p><b>入力した値を落とさない。</b> 新しい到着予定日と対応方針は画面で
     * 入力させ、コマンドとイベントまで運んでいる——ここで捨てると、入力した値が
     * 最後の層で消える（IT10 のレビューで実測）。とくに新しい到着予定日は
     * 一覧の並び（残日数が少ない順・不変条件 7）に効く。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tracking_exception SET response_status = #{responseStatus}, "
            + "new_estimated_arrival = COALESCE("
            + "  CAST(#{newEstimatedArrival} AS DATE), new_estimated_arrival), "
            + "response_plan = #{responsePlan}, "
            + "projected_at = #{projectedAt} WHERE exception_id = #{exceptionId}")
    int updateResponseStatus(@Param("exceptionId") String exceptionId,
            @Param("responseStatus") String responseStatus,
            @Param("newEstimatedArrival") String newEstimatedArrival,
            @Param("responsePlan") String responsePlan,
            @Param("projectedAt") Instant projectedAt);

    /** 解決した。対応内容と解決日時を足す。<b>起票の内容は残る</b>（不変条件 6）。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tracking_exception SET response_status = #{responseStatus}, "
            + "resolution = #{resolution}, resolved_at = #{resolvedAt}, "
            + "projected_at = #{projectedAt} WHERE exception_id = #{exceptionId}")
    int resolve(@Param("exceptionId") String exceptionId,
            @Param("responseStatus") String responseStatus,
            @Param("resolution") String resolution,
            @Param("resolvedAt") Instant resolvedAt,
            @Param("projectedAt") Instant projectedAt);

    @Select("SELECT " + COLUMNS + " FROM tracking_exception WHERE exception_id = #{exceptionId}")
    TrackingExceptionRow findById(@Param("exceptionId") String exceptionId);

    /** その追跡の例外を<b>起きた順</b>に返す（S41 の例外欄）。 */
    @Select("SELECT " + COLUMNS + " FROM tracking_exception "
            + "WHERE tracking_number = #{trackingNumber} ORDER BY occurred_at, exception_id")
    List<TrackingExceptionRow> findByTracking(@Param("trackingNumber") String trackingNumber);

    /**
     * 未解決の例外（S42 / {@code FindOpenExceptionsQuery}）。
     *
     * <p><b>並びは緊急が先、以降は到着期限までの残日数が少ない順</b>（不変条件 7）。
     * 残日数は {@code tracking_exception} だけでは出せない——到着期限は
     * {@code tracking_summary} が持つので JOIN する（data-model.md の注 N4）。</p>
     *
     * <p><b>既定で解決済を外す。</b> 決着したものが混ざると、一覧全体が
     * 「まだ手を入れる場所」に見えなくなる。</p>
     */
    @Select("SELECT " + OPEN_COLUMNS + " FROM tracking_exception x "
            + "JOIN tracking_summary s ON s.tracking_number = x.tracking_number "
            + "WHERE x.response_status <> 'RESOLVED' "
            // **対応で期限が動いたら、その日付で並べる。** 古い期限のまま並べると、
            // 対応済みのものが「まだ急ぎ」の位置に残る（IT10 レビュー 高）。
            + "ORDER BY x.urgent DESC, "
            + "  COALESCE(x.new_estimated_arrival, CAST(s.estimated_arrival AS DATE)) "
            + "    ASC NULLS LAST, "
            + "  x.occurred_at")
    List<OpenExceptionRow> findOpen();

    /** 一覧の 1 行。到着期限は追跡から持って来る（残日数の並びに要る）。 */
    String OPEN_COLUMNS = "x.exception_id, x.tracking_number, x.exception_type, "
            + "x.response_status, x.urgent, x.unlocode, x.description, x.occurred_at, "
            // **並びの根拠を、そのまま画面に出す。** 対応で動いた期限が
            // 見えないと、なぜその順なのか読めない。
            + "COALESCE(x.new_estimated_arrival, CAST(s.estimated_arrival AS DATE)) "
            + "  AS estimated_arrival, "
            + "s.transport_status";

    /** 例外 1 件（投影の行）。 */
    record TrackingExceptionRow(
            String exceptionId,
            String trackingNumber,
            String exceptionType,
            String responseStatus,
            boolean urgent,
            String unlocode,
            String description,
            String resolution,
            // 対応で示した新しい到着予定日（US19 §4）。一覧の並びに効く。
            java.time.LocalDate newEstimatedArrival,
            String responsePlan,
            Instant occurredAt,
            Instant resolvedAt,
            Instant projectedAt) {
    }

    /** 例外一覧の 1 行（S42）。 */
    record OpenExceptionRow(
            String exceptionId,
            String trackingNumber,
            String exceptionType,
            String responseStatus,
            boolean urgent,
            String unlocode,
            String description,
            Instant occurredAt,
            java.time.LocalDate estimatedArrival,
            String transportStatus) {
    }
}
