package com.example.cargotracker.handling.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 荷役の読み取りモデル（data-model.md「handling_read_db」）。 */
@Mapper
public interface CargoSnapshotMapper {

    /**
     * 読み出す列を並べる。<b>{@code SELECT *} にしない。</b>
     *
     * <p>record への割り当ては列の順で決まる。あとから {@code ALTER TABLE} で足した
     * 列は末尾に来るので、record の途中に項目を足すと全部ずれる（IT8 で実測）。</p>
     */
    String COLUMNS = "tracking_number, booking_id, origin_unlocode, destination_unlocode, "
            + "cargo_type, cancelled, projected_at, last_event_id";

    String LEG_COLUMNS = "tracking_number, leg_seq, voyage_number, load_unlocode, unload_unlocode";

    int insert(CargoSnapshotRow row);

    @Select("SELECT " + COLUMNS + " FROM cargo_snapshot WHERE tracking_number = #{trackingNumber}")
    CargoSnapshotRow findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    void insertLegs(@Param("trackingNumber") String trackingNumber,
            @Param("legs") List<CargoSnapshotLegRow> legs);

    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM cargo_snapshot_leg WHERE tracking_number = #{trackingNumber}")
    int deleteLegs(@Param("trackingNumber") String trackingNumber);

    /** 予定の旅程。<b>積む順</b>に返す（順序が業務の意味を持つ）。 */
    @Select("SELECT " + LEG_COLUMNS + " FROM cargo_snapshot_leg "
            + "WHERE tracking_number = #{trackingNumber} ORDER BY leg_seq")
    List<CargoSnapshotLegRow> findLegs(@Param("trackingNumber") String trackingNumber);

    /**
     * この航海がこの港で降ろす貨物（S50 の起点。`FindCargosOnVoyageQuery`）。
     *
     * <p><b>追跡番号は現場が持っていない。</b> 荷役作業員は船と港から始めて、
     * 貨物はスキャンで特定する。ここが引けないと画面が始まらない。</p>
     *
     * <p>キャンセルされた貨物は出さない（作業の対象ではない）。</p>
     */
    @Select("SELECT s.tracking_number, s.booking_id, s.origin_unlocode, s.destination_unlocode, "
            + "s.cargo_type, s.cancelled, s.projected_at, s.last_event_id "
            + "FROM cargo_snapshot s JOIN cargo_snapshot_leg l "
            + "  ON l.tracking_number = s.tracking_number "
            + "WHERE l.voyage_number = #{voyageNumber} AND l.unload_unlocode = #{unLocode} "
            + "  AND s.cancelled = FALSE "
            + "ORDER BY s.tracking_number")
    List<CargoSnapshotRow> findOnVoyage(@Param("voyageNumber") String voyageNumber,
            @Param("unLocode") String unLocode);

    /** 貨物の写し。Booking / Tracking の型は持ち込まない。 */
    record CargoSnapshotRow(
            String trackingNumber,
            String bookingId,
            String originUnlocode,
            String destinationUnlocode,
            String cargoType,
            boolean cancelled,
            Instant projectedAt,
            String lastEventId) {
    }

    /** 予定の旅程の 1 区間。<b>時刻は持たない</b>（ADR-0012 決定 4）。 */
    record CargoSnapshotLegRow(
            String trackingNumber,
            int legSeq,
            String voyageNumber,
            String loadUnlocode,
            String unloadUnlocode) {
    }
}
