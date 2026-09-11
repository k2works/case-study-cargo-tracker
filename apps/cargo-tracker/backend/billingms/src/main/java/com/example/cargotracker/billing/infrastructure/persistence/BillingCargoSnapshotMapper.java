package com.example.cargotracker.billing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 請求が読む貨物スナップショット（data-model.md「billing_read_db」）。 */
@Mapper
public interface BillingCargoSnapshotMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "tracking_number, booking_id, shipper_id, origin_unlocode, "
            + "destination_unlocode, cargo_type, weight_kg, projected_at, last_event_id";

    /**
     * 冪等に書く。少なくとも 1 回配送なので、同じイベントが 2 度届きうる。
     * リプレイでも同じ結果になるよう、常に上書きする。
     */
    int upsert(SnapshotRow row);

    @Select("SELECT " + COLUMNS + " FROM billing_cargo_snapshot "
            + "WHERE tracking_number = #{trackingNumber}")
    SnapshotRow find(@Param("trackingNumber") String trackingNumber);

    /** 予約から引く（請求は引取済の予約に対して作る）。 */
    @Select("SELECT " + COLUMNS + " FROM billing_cargo_snapshot "
            + "WHERE booking_id = #{bookingId}")
    SnapshotRow findByBooking(@Param("bookingId") String bookingId);

    /**
     * 区間を入れ直す前に消す。
     *
     * <p><b>追記専用の行はリプレイで増える</b>（IT6 で実際に踏んだ）。区間は
     * 貨物ごとに作り直す。</p>
     */
    @Delete("DELETE FROM billing_cargo_leg WHERE tracking_number = #{trackingNumber}")
    int deleteLegs(@Param("trackingNumber") String trackingNumber);

    int insertLeg(LegRow leg);

    /** 積む順に返す。<b>順序が業務の意味を持つ</b>（地域係数は区間ごとに数える）。 */
    @Select("SELECT tracking_number, leg_seq, load_unlocode, unload_unlocode "
            + "FROM billing_cargo_leg WHERE tracking_number = #{trackingNumber} "
            + "ORDER BY leg_seq")
    List<LegRow> findLegs(@Param("trackingNumber") String trackingNumber);

    record SnapshotRow(
            String trackingNumber,
            String bookingId,
            String shipperId,
            String originUnLocode,
            String destinationUnLocode,
            String cargoType,
            BigDecimal weightKg,
            Instant projectedAt,
            String lastEventId) {
    }

    record LegRow(
            String trackingNumber,
            int legSeq,
            String loadUnLocode,
            String unloadUnLocode) {
    }
}
