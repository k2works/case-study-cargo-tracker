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
    String COLUMNS = "tracking_number, booking_id, shipper_id, origin_unlocode, destination_unlocode, cargo_type, transport_status, initialized_at, last_status_changed_at, projected_at, last_event_id";

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
            + "last_status_changed_at = #{lastStatusChangedAt}, projected_at = #{projectedAt}, "
            + "last_event_id = #{lastEventId} WHERE tracking_number = #{trackingNumber}")
    int updateStatus(@Param("trackingNumber") String trackingNumber,
            @Param("transportStatus") String transportStatus,
            @Param("lastStatusChangedAt") Instant lastStatusChangedAt,
            @Param("projectedAt") Instant projectedAt,
            @Param("lastEventId") String lastEventId);

    void insertLegs(@Param("trackingNumber") String trackingNumber,
            @Param("legs") List<TrackingLegRow> legs);

    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM tracking_leg WHERE tracking_number = #{trackingNumber}")
    int deleteLegs(@Param("trackingNumber") String trackingNumber);

    /**
     * 荷主の追跡を新しい順に返す（US18）。<b>自社の貨物だけ</b>。
     *
     * <p><b>絞り込みは SQL で行う。</b> 全件を読んでから捨てると、件数が増えたときに
     * 他社の行がメモリに載り、絞り忘れが情報漏れになる。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM tracking_summary WHERE shipper_id = #{shipperId} "
            + "ORDER BY initialized_at DESC")
    List<TrackingSummaryRow> findByShipper(@Param("shipperId") String shipperId);

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
            Instant initializedAt,
            Instant lastStatusChangedAt,
            Instant projectedAt,
            String lastEventId) {
    }

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
