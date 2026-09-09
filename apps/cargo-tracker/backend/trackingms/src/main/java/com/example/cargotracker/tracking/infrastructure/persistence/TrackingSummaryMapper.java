package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 追跡の投影（US14）。正典は data-model.md「tracking_read_db ER 図」。 */
@Mapper
public interface TrackingSummaryMapper {

    /**
     * 読み出す列を並べる。<b>{@code SELECT *} にしない。</b>
     *
     * <p>record への割り当ては<b>列の順</b>で決まる。{@code SELECT *} はテーブルの
     * 定義順を返すので、あとから {@code ALTER TABLE ADD COLUMN} で足した列は末尾に来る。
     * record の途中に項目を足した瞬間、全部が 1 つずつずれる（IT8 T5 で実測。
     * 「日時の列に evt-1 が入らない」という、原因の読めない形で落ちた）。</p>
     */
    String COLUMNS = "tracking_number, booking_id, shipper_id, origin_unlocode, "
            + "destination_unlocode, cargo_type, transport_status, current_unlocode, "
            + "estimated_arrival, initialized_at, last_status_changed_at, projected_at, "
            + "last_event_id, open_exception_count, urgent_exception_count, "
            + "status_before_exception, misrouted";

    /**
     * 追跡を作る（US14）。
     *
     * <p><b>リプレイで増えない形にする。</b> 主キーは追跡番号なので、同じイベントを
     * 2 度読んでも行は 1 つのまま。旅程も同じ理由で先に消してから入れ直す。</p>
     */
    int insert(TrackingSummaryRow row);

    @Select("SELECT " + COLUMNS + " FROM tracking_summary WHERE tracking_number = #{trackingNumber}")
    TrackingSummaryRow findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    /** 予約から引く。**連鎖が通ったかの確認**と、予約詳細からの導線に使う。 */
    @Select("SELECT " + COLUMNS + " FROM tracking_summary WHERE booking_id = #{bookingId}")
    TrackingSummaryRow findByBooking(@Param("bookingId") String bookingId);

    /**
     * 現在の状態を書き換える（US17）。
     *
     * <p><b>行が無ければ何もしない</b>（呼ぶ側が確かめる）。追跡が作られる前に
     * 状態の更新が届く順序は起きないが、起きたときに空の行を作ると、出発地も
     * 目的地も無い追跡が一覧に出る。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tracking_summary SET transport_status = #{transportStatus}, "
            + "last_status_changed_at = #{lastStatusChangedAt}, "
            // **場所が空の更新で現在地を消さない**（COALESCE）。場所は任意入力で、
            // 空のまま 1 回更新しただけで分かっていた現在地が消えていた。
            + "current_unlocode = COALESCE(#{currentUnlocode}, current_unlocode), "
            + "projected_at = #{projectedAt}, "
            + "last_event_id = #{lastEventId} WHERE tracking_number = #{trackingNumber}")
    int updateStatus(@Param("trackingNumber") String trackingNumber,
            @Param("transportStatus") String transportStatus,
            @Param("lastStatusChangedAt") Instant lastStatusChangedAt,
            @Param("currentUnlocode") String currentUnlocode,
            @Param("projectedAt") Instant projectedAt,
            @Param("lastEventId") String lastEventId);

    /**
     * 例外の件数を数え直す（US19）。
     *
     * <p><b>足し引きしない。</b> 前回の記入漏れは赤くならず、足し算で書くと
     * 永久に残る（IT4 の「インデックスの累計は明細から導く」）。明細から数える。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tracking_summary SET "
            + "open_exception_count = (SELECT COUNT(*) FROM tracking_exception x "
            + "  WHERE x.tracking_number = #{trackingNumber} "
            + "    AND x.response_status <> 'RESOLVED'), "
            + "urgent_exception_count = (SELECT COUNT(*) FROM tracking_exception x "
            + "  WHERE x.tracking_number = #{trackingNumber} "
            + "    AND x.response_status <> 'RESOLVED' AND x.urgent = TRUE), "
            + "status_before_exception = #{statusBeforeException}, "
            + "projected_at = #{projectedAt} WHERE tracking_number = #{trackingNumber}")
    int refreshExceptionCounts(@Param("trackingNumber") String trackingNumber,
            @Param("statusBeforeException") String statusBeforeException,
            @Param("projectedAt") Instant projectedAt);

    void insertLegs(@Param("trackingNumber") String trackingNumber,
            @Param("legs") List<TrackingLegRow> legs);

    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM tracking_leg WHERE tracking_number = #{trackingNumber}")
    int deleteLegs(@Param("trackingNumber") String trackingNumber);

    /**
     * 追跡一覧（S40）。<b>荷主が指定されていれば自社のぶんだけ</b>。
     *
     * <p><b>絞り込みは SQL で行う。</b> 全件を読んでから捨てると、件数が増えたときに
     * 他社の行がメモリに載り、絞り忘れが情報漏れになる。</p>
     *
     * <p>引取済（{@code DELIVERED}）は既定で外す。引き取られた貨物が混ざると、
     * 一覧全体が「いま追うもの」として信用されなくなる（ui_design.md）。</p>
     *
     * <p><b>到着予定が近い順に並べる</b>（ui_design.md「一覧の既定条件」）。最終更新の
     * 新しい順にすると「さっき自分が触ったもの」が上に来て、<b>誰も触っていない＝
     * いちばん危ないものが最下段に沈む</b>。追跡管理者が朝いちばんに見たいのは
     * 「今日・明日着く貨物」と「止まっているもの」である。</p>
     *
     * <p>到着予定は<b>予定の旅程の最終区間の荷降し</b>（区間は積む順なので最大値）。
     * 旅程が無い行は末尾に置く——判断の材料が無いものを先頭に出しても仕事が進まない。</p>
     */
    @Select({"<script>",
        "SELECT " + COLUMNS + " FROM tracking_summary",
        "<where>",
        "  <if test='shipperId != null'>AND shipper_id = #{shipperId}</if>",
        "  <if test='!includeDelivered'>AND transport_status &lt;&gt; 'DELIVERED'</if>",
        "</where>",
        " ORDER BY estimated_arrival ASC NULLS LAST, last_status_changed_at DESC",
        " LIMIT #{limit}",
        "</script>"})
    List<TrackingSummaryRow> findAll(@Param("shipperId") String shipperId,
            @Param("includeDelivered") boolean includeDelivered,
            @Param("limit") int limit);

    /**
     * 一覧の対象件数（S40）。<b>上限で切れていることを黙らないため</b>に数える。
     *
     * <p>件数が上限を超えると、出ていない貨物は<b>誰も追わない</b>——追跡管理者は
     * 「一覧に出ていない＝無い」と読む。</p>
     */
    @Select({"<script>",
        "SELECT count(*) FROM tracking_summary",
        "<where>",
        "  <if test='shipperId != null'>AND shipper_id = #{shipperId}</if>",
        "  <if test='!includeDelivered'>AND transport_status &lt;&gt; 'DELIVERED'</if>",
        "</where>",
        "</script>"})
    int countAll(@Param("shipperId") String shipperId,
            @Param("includeDelivered") boolean includeDelivered);

    /**
     * 直近で状態が変わった件数（S02 荷主 / US17 §受入基準 4 の代わり）。
     *
     * <p><b>荷主には「変わったこと」を知る手段がない。</b> 送信基盤はスコープ外
     * なので、一覧を毎回見比べるしかなかった（IT8 のレビュー指摘）。件数を出して
     * 一覧へ繋げば、送信基盤なしで満たせる。</p>
     */
    @Select("SELECT count(*) FROM tracking_summary "
            + "WHERE shipper_id = #{shipperId} AND last_status_changed_at >= #{since}")
    int countRecentlyChanged(@Param("shipperId") String shipperId,
            @Param("since") Instant since);

    /** 予定の旅程。**積む順**に返す（順序が業務の意味を持つ）。 */
    @Select("SELECT tracking_number, leg_seq, voyage_number, load_unlocode, "
            + "unload_unlocode, load_time, unload_time FROM tracking_leg "
            + "WHERE tracking_number = #{trackingNumber} "
            + "ORDER BY leg_seq")
    List<TrackingLegRow> findLegs(@Param("trackingNumber") String trackingNumber);

    /** 投影の行。本 IT で書く列だけを持つ。 */
    record TrackingSummaryRow(
            String trackingNumber,
            String bookingId,
            String shipperId,
            String originUnlocode,
            String destinationUnlocode,
            String cargoType,
            String transportStatus,
            String currentUnlocode,
            Instant estimatedArrival,
            Instant initializedAt,
            Instant lastStatusChangedAt,
            Instant projectedAt,
            String lastEventId,
            // 例外の件数（US19）。**一覧が tracking_exception を数えないための写し**。
            int openExceptionCount,
            int urgentExceptionCount,
            // 例外前の状態。画面が「解決すると何に戻るか」を出すために持つ。
            // 戻る先そのものは集約が覚えている（不変条件 5）。
            String statusBeforeException,
            // 誤配として扱っているか（US28）。バナー（S22・S41）が読む。
            // **状態（MISROUTED）とは別に持つ**——例外の対応中は状態が
            // EXCEPTION に退避するが、誤配であることは変わらない。
            boolean misrouted) {
    }

    /**
     * 誤配として扱う（US28 §受入基準 2）。
     *
     * <p><b>状態とは別に立てる。</b> 例外の対応中は状態が {@code EXCEPTION} へ
     * 退避するが、誤配であることは変わらない。状態から導くと、バナーが
     * 例外の起票と同時に消える。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tracking_summary SET misrouted = TRUE, current_unlocode = #{unLocode}, "
            + "projected_at = #{projectedAt} WHERE tracking_number = #{trackingNumber}")
    int markMisrouted(@Param("trackingNumber") String trackingNumber,
            @Param("unLocode") String unLocode,
            @Param("projectedAt") Instant projectedAt);

    /** 誤配が解けた（再設計で経路が確定した）。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tracking_summary SET misrouted = FALSE, projected_at = #{projectedAt} "
            + "WHERE tracking_number = #{trackingNumber}")
    int clearMisrouted(@Param("trackingNumber") String trackingNumber,
            @Param("projectedAt") Instant projectedAt);

    /** 予定の旅程の 1 区間。 */
    record TrackingLegRow(
            String trackingNumber,
            int legSeq,
            String voyageNumber,
            String loadUnlocode,
            String unloadUnlocode,
            Instant loadTime,
            Instant unloadTime) {
    }
}
