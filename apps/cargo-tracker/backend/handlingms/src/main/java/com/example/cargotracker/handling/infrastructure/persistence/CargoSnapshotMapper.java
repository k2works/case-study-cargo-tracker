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
            + "cargo_type, cancelled, projected_at, last_event_id, "
            // シミュレーション由来か（ADR-0020 決定 4）。**末尾に足す**——
            // record は位置で割り当てるので、途中に挟むと全部ずれる。
            + "cancellation_discharge_unlocode, simulated";

    String LEG_COLUMNS = "tracking_number, leg_seq, voyage_number, load_unlocode, unload_unlocode";

    /**
     * 貨物の写しを作る。
     *
     * <p><b>荷主は行に持たない。</b> {@code cargo_snapshot} は荷主を持たない
     * （荷役の仕事に荷主は要らない）が、由来の印を写しから解決するために
     * <b>書き込みのときだけ</b>荷主が要る。列に足さず、引数で受ける。</p>
     */
    int insert(@Param("row") CargoSnapshotRow row, @Param("shipperId") String shipperId);

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
            + "s.cargo_type, s.cancelled, s.projected_at, s.last_event_id, "
            + "s.cancellation_discharge_unlocode, s.simulated "
            + "FROM cargo_snapshot s JOIN cargo_snapshot_leg l "
            + "  ON l.tracking_number = s.tracking_number "
            + "WHERE l.voyage_number = #{voyageNumber} "
            // **積む港からも入る。** 降ろす港だけで引くと、受領と積込の作業を
            // する港からは画面が始まらない（種別は 3 つ選べるのに対象が出ない）。
            + "  AND (l.load_unlocode = #{unLocode} OR l.unload_unlocode = #{unLocode}) "
            // **キャンセルされた貨物は外す。ただし指定された陸揚げ地は残す。**
            // 全部の港から外すと、現場は「この港で降ろす」ことを知る手段を
            // 持たない（IT15 のレビュー 高）。
            + "  AND (s.cancelled = FALSE "
            + "       OR s.cancellation_discharge_unlocode = #{unLocode}) "
            // シミュレーション由来は外す（ADR-0020 決定 4）。荷役の一覧は
            // **荷主で絞られない**ので、外さなければ本物の作業に混ざる。
            + "  AND s.simulated = FALSE "
            + "ORDER BY s.tracking_number")
    List<CargoSnapshotRow> findOnVoyage(@Param("voyageNumber") String voyageNumber,
            @Param("unLocode") String unLocode);

    /**
     * 作業する予定のある航海と港（S02 荷役のダッシュボード）。
     *
     * <p><b>航海の一覧は routingms が持つが、荷役ロールはそこを読めない</b>
     * （`/routing/voyages` は経路設計者だけ）。荷役が見たいのは「自分が扱う貨物が
     * ある航海」なので、写しから引く。</p>
     *
     * <p><b>積む港と降ろす港の両方</b>を出す。降ろす港だけだと、受領と積込を
     * する港がダッシュボードに現れない。</p>
     *
     * <p><b>「本日」では絞れない。</b> 写しは予定の時刻を持たない（[ADR-0012]
     * 決定 4——航海の予定は routingms が変えるので、写した時刻は黙って古くなる）。
     * 出せるのは「作業のある航海」までで、見出しもそう書く。</p>
     *
     * <p>キャンセルされた貨物は数えない（作業の対象ではない）。</p>
     */
    @Select("SELECT voyage_number, unlocode, count(*) AS cargo_count FROM ("
            + "  SELECT l.voyage_number, l.load_unlocode AS unlocode, l.tracking_number "
            + "  FROM cargo_snapshot_leg l JOIN cargo_snapshot s "
            + "    ON s.tracking_number = l.tracking_number "
            // 積む港は外す（止まった貨物を積み続けない）。
            + "  WHERE s.cancelled = FALSE AND s.simulated = FALSE "
            + "  UNION ALL "
            + "  SELECT l.voyage_number, l.unload_unlocode AS unlocode, l.tracking_number "
            + "  FROM cargo_snapshot_leg l JOIN cargo_snapshot s "
            + "    ON s.tracking_number = l.tracking_number "
            // 降ろす港は、キャンセルされた貨物の陸揚げ地も数える——
            // ダッシュボードがその港への唯一の入口である。
            + "  WHERE s.simulated = FALSE "
            + "    AND (s.cancelled = FALSE "
            + "         OR s.cancellation_discharge_unlocode = l.unload_unlocode)"
            + ") ports "
            + "GROUP BY voyage_number, unlocode "
            + "ORDER BY voyage_number, unlocode "
            // **上限を置く**（IT10 レビュー N6）。全航海・全港を無制限に返すと、
            // 運用日数に比例して伸びる——荷役のダッシュボードは毎朝ここを開く。
            // 上限に当たったことは呼び出し側が数えて画面に知らせる（黙って切らない）。
            + "LIMIT #{limit}")
    List<VoyagePortRow> findVoyagePorts(@Param("limit") int limit);

    /**
     * その港で引取を待っている貨物（H.8 / US16）。
     *
     * <p><b>目的港で荷降しが済み、引取がまだのもの。</b> 引取は船から降りたあとの
     * 作業なので、航海起点（S50）では辿り着けない——どの航海の仕事でもない。</p>
     *
     * <p>取り消された記録は数えない。取り消したのに「引取待ちから外れたまま」に
     * すると、その貨物は誰にも引き取られない。</p>
     */
    @Select("SELECT s.tracking_number, s.booking_id, s.origin_unlocode, "
            + "s.destination_unlocode, s.cargo_type, s.cancelled, s.projected_at, "
            + "s.last_event_id, s.cancellation_discharge_unlocode, s.simulated "
            + "FROM cargo_snapshot s "
            + "WHERE s.cancelled = FALSE "
            + "  AND s.simulated = FALSE "
            + "  AND s.destination_unlocode = #{unLocode} "
            + "  AND EXISTS (SELECT 1 FROM handling_activity a "
            + "              WHERE a.tracking_number = s.tracking_number "
            + "                AND a.handling_type = 'UNLOAD' "
            + "                AND a.unlocode = #{unLocode} AND a.voided = FALSE) "
            + "  AND NOT EXISTS (SELECT 1 FROM handling_activity a "
            + "                  WHERE a.tracking_number = s.tracking_number "
            + "                    AND a.handling_type = 'CLAIM' AND a.voided = FALSE) "
            + "ORDER BY s.tracking_number")
    List<CargoSnapshotRow> findAwaitingClaim(@Param("unLocode") String unLocode);

    /** 航海と港の組（S02 荷役）。**積む港と降ろす港の両方**が入る。 */
    record VoyagePortRow(String voyageNumber, String unlocode, int cargoCount) {
    }

    /**
     * キャンセルの印を付ける（US30 / ADR-0012 決定 3。IT15 T7）。
     *
     * <p><b>行は消さない。</b> 消すと、記録済みの荷役が「どの貨物のものか」を
     * 辿れなくなる。読み口の側が {@code cancelled = FALSE} で絞っているので、
     * 印を付けるだけで作業一覧から外れる。</p>
     *
     * <p><b>陸揚げ地も一緒に書く</b>（US30）。**ここで降ろす作業だけは残す**
     * ——全部の港から外すと、現場は降ろす港を知る手段を持たない。輸送開始前の
     * キャンセルでは陸揚げ地が無く、NULL のままでよい（船に載っていない）。</p>
     *
     * <p><b>二度届いても同じ。</b> 同じ値を入れ直すだけである。</p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_snapshot SET cancelled = TRUE, "
            + "cancellation_discharge_unlocode = #{dischargeUnLocode}, "
            + "projected_at = #{projectedAt} "
            + "WHERE booking_id = #{bookingId}")
    int markCancelled(@Param("bookingId") String bookingId,
            @Param("dischargeUnLocode") String dischargeUnLocode,
            @Param("projectedAt") java.time.Instant projectedAt);

    /** 貨物の写し。Booking / Tracking の型は持ち込まない。 */

    record CargoSnapshotRow(
            String trackingNumber,
            String bookingId,
            String originUnlocode,
            String destinationUnlocode,
            String cargoType,
            boolean cancelled,
            Instant projectedAt,
            String lastEventId,
            // 承認されたキャンセルの陸揚げ地（US30）。**ここで降ろす作業だけは
            // 残る**——降ろさなければ貨物は船の上に残り、追跡も閉じない。
            // NULL はキャンセルされていない貨物。
            String cancellationDischargeUnlocode,
            // シミュレーション由来か（ADR-0020 決定 4）。作業一覧からは外すが、
            // 写しそのものは作る——実行結果（S93）が貨物を辿れなくなる。
            boolean simulated) {
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
