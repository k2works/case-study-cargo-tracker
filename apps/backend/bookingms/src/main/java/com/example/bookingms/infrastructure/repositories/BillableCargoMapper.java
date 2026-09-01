package com.example.bookingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 料金算出の対象になる予約を引く（US21・[ADR-027] 決定 5・決定 7）。
 *
 * <p><strong>読み取り専用のクエリである</strong>（CQRS のクエリ側）。集約を経由せず、
 * 予約・荷主・キャンセル申請を JOIN した 1 行をそのまま返す——請求のために貨物の状態を
 * 変えることはない。
 *
 * <p><strong>絞りは SQL に置く。</strong>引取済（{@code DELIVERED}）とキャンセル済み
 * （{@code CANCELLED}）だけを返す。呼び出し側で絞ると、画面と API で別々の条件を持つ
 * ことになる。
 */
@Mapper
public interface BillableCargoMapper {

    /**
     * 料金算出の対象。
     *
     * <p><strong>キャンセル申請は「承認済みの最新 1 件」だけを引く。</strong>却下されて
     * 再申請した予約では複数行あり、絞らないと予約が重複して並ぶ。料率は<strong>申請した
     * 時点</strong>の状態で決まる（正典のビジネスルール 6）。
     */
    String BASE_QUERY = """
            SELECT c.booking_id                        AS booking_id,
                   c.booking_status                    AS booking_status,
                   s.id                                AS shipper_id,
                   s.name                              AS shipper_name,
                   s.shipper_code                      AS shipper_code,
                   s.shipper_type                      AS shipper_type,
                   s.discount_rate                     AS discount_rate,
                   c.weight_kg                         AS weight_kg,
                   c.cargo_type                        AS cargo_type,
                   origin.name                         AS origin_name,
                   origin.country_code                 AS origin_country,
                   destination.name                    AS destination_name,
                   destination.country_code            AS destination_country,
                   (SELECT COUNT(*) FROM leg l WHERE l.cargo_id = c.id) AS leg_count,
                   c.last_handling_at                  AS claimed_at,
                   c.misrouted_at                      AS misrouted_at,
                   c.misrouted_location_unlocode       AS misrouted_location_unlocode,
                   misrouted_location.name             AS misrouted_location_name,
                   cr.booking_status_at_request        AS cancelled_at_status,
                   cr.requested_at                     AS cancellation_requested_at
              FROM cargo c
              JOIN shipper s
                ON s.id = c.shipper_id
              LEFT JOIN location origin
                ON origin.unlocode = c.spec_origin_unlocode
              LEFT JOIN location destination
                ON destination.unlocode = c.spec_destination_unlocode
              LEFT JOIN location misrouted_location
                ON misrouted_location.unlocode = c.misrouted_location_unlocode
              LEFT JOIN cancellation_request cr
                ON cr.id = (SELECT r.id
                              FROM cancellation_request r
                             WHERE r.cargo_id = c.id
                               AND r.status = 'APPROVED'
                             ORDER BY r.requested_at DESC, r.id DESC
                             LIMIT 1)
             WHERE c.booking_status IN ('DELIVERED', 'CANCELLED')
            """;

    @Select(BASE_QUERY + " AND c.booking_id = #{bookingId}")
    @Results(id = "billableCargo", value = {
            @Result(column = "booking_id", property = "bookingId"),
            @Result(column = "booking_status", property = "bookingStatus"),
            @Result(column = "shipper_id", property = "shipperId"),
            @Result(column = "shipper_name", property = "shipperName"),
            @Result(column = "shipper_type", property = "shipperType"),
            @Result(column = "discount_rate", property = "discountRate"),
            @Result(column = "weight_kg", property = "weightKg"),
            @Result(column = "cargo_type", property = "cargoType"),
            @Result(column = "origin_name", property = "originName"),
            @Result(column = "origin_country", property = "originCountry"),
            @Result(column = "destination_name", property = "destinationName"),
            @Result(column = "destination_country", property = "destinationCountry"),
            @Result(column = "leg_count", property = "legCount"),
            @Result(column = "claimed_at", property = "claimedAt"),
            @Result(column = "misrouted_at", property = "misroutedAt"),
            @Result(column = "misrouted_location_unlocode", property = "misroutedLocationUnlocode"),
            @Result(column = "shipper_code", property = "shipperCode"),
            @Result(column = "misrouted_location_name", property = "misroutedLocationName"),
            @Result(column = "cancelled_at_status", property = "cancelledAtStatus"),
            @Result(column = "cancellation_requested_at", property = "cancellationRequestedAt"),
    })
    BillableCargoRecord selectByBookingId(@Param("bookingId") String bookingId);

    /**
     * 対象をすべて並べる。
     *
     * <p><strong>引取が終わった順に並べる。</strong>待たせている案件が上に来る。
     * キャンセルは引取日時を持たないため最後に回る（{@code NULLS LAST}）。
     *
     * <p><strong>シミュレーション由来は締め対象に出さない</strong>（[ADR-030] 決定 3）。
     * 混ざると、経理担当者の締めに実在しない輸送の請求が乗る。
     * <strong>名指しの照会（{@code selectByBookingId}）では外さない</strong>——
     * 外すと、シミュレーション自身の料金算出が通らず、精算まで通ることを確かめられない。
     */
    @Select(BASE_QUERY + """
               AND s.shipper_code NOT LIKE 'SIM-%'
             ORDER BY CASE WHEN c.last_handling_at IS NULL THEN 1 ELSE 0 END,
                      c.last_handling_at,
                      c.booking_id
            """)
    @ResultMap("billableCargo")
    List<BillableCargoRecord> selectAllBillable();

    /**
     * 旅程の区間（[ADR-027] 決定 1 の改訂）。
     *
     * <p><strong>IN リストで渡さない。</strong>動的 SQL（{@code <foreach>}）にすると、
     * 方言スモークが SQL を組み立てられず（引数が無いため）、<strong>全クエリの検査が
     * その 1 本で落ちる</strong>。絞りは対象の予約と同じ条件で書ける。
     *
     * <p><strong>順序どおりに返す</strong>（{@code seq_number}）。旅程は
     * 「東京 → 釜山 → ロサンゼルス」のように順序に意味がある。
     */
    String LEG_QUERY = """
            SELECT c.booking_id                    AS booking_id,
                   load_location.region            AS load_region,
                   unload_location.region          AS unload_region
              FROM leg l
              JOIN cargo c
                ON c.id = l.cargo_id
              JOIN location load_location
                ON load_location.unlocode = l.load_location_unlocode
              JOIN location unload_location
                ON unload_location.unlocode = l.unload_location_unlocode
             WHERE c.booking_status IN ('DELIVERED', 'CANCELLED')
            """;

    @Select(LEG_QUERY + """
             AND c.booking_id = #{bookingId}
             ORDER BY l.seq_number
            """)
    @Results(id = "billableLeg", value = {
            @Result(column = "booking_id", property = "bookingId"),
            @Result(column = "load_region", property = "loadRegion"),
            @Result(column = "unload_region", property = "unloadRegion"),
    })
    List<BillableLegRecord> selectLegsByBookingId(@Param("bookingId") String bookingId);

    /**
     * 対象すべての区間を 1 回で引く。
     *
     * <p><strong>1 件ずつ引かない。</strong>対象が増えるほど問い合わせが増える形にすると、
     * 経理担当者が毎朝開く一覧が重くなる。
     */
    @Select(LEG_QUERY + " ORDER BY c.booking_id, l.seq_number")
    @ResultMap("billableLeg")
    List<BillableLegRecord> selectAllBillableLegs();
}
