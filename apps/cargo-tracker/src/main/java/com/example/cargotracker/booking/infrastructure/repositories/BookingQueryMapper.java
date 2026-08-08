package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.application.internal.queryservices.BookingSearchCriteria;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 貨物予約の読み取り専用マッパー（CQRS のクエリ側）。
 *
 * <p>荷主名は JOIN で 1 回の SQL に含める。予約 1 件ごとに荷主を引き直すと
 * 一覧で N+1 になる。
 *
 * <p><strong>状態の表示名とキャンセル可否は SQL で組み立てない。</strong>
 * 遷移表の規則を SQL にも書くと、規則が 2 か所に散って必ず片方だけが更新される。
 * ここでは列挙子名までを返し、表示名と可否は {@code BookingStatus} から導く。
 */
@Mapper
public interface BookingQueryMapper {

    String SELECT_ROW = """
            SELECT CAST(c.booking_id AS VARCHAR) AS bookingId,
                   CAST(c.shipper_id AS VARCHAR) AS shipperId,
                   s.shipper_code                AS shipperCode,
                   s.name                        AS shipperName,
                   -- 通知の宛先（US12）。**ACL ポートでは運ばない。**
                   -- ShipperExistenceChecker は「存在するか」だけを返す約束であり、
                   -- 表示・通知に要る値は CQRS のクエリ側が読む（同ポートの規約）
                   s.email                       AS shipperEmail,
                   c.cargo_type                  AS cargoType,
                   c.weight                      AS weight,
                   c.origin_unlocode             AS origin,
                   c.destination_unlocode        AS destination,
                   c.arrival_deadline            AS arrivalDeadline,
                   c.booking_status              AS bookingStatus,
                   c.routing_status              AS routingStatus,
                   c.tracking_number             AS trackingNumber,
                   c.dimension_length            AS dimensionLength,
                   c.dimension_width             AS dimensionWidth,
                   c.dimension_height            AS dimensionHeight,
                   c.quantity                    AS quantity,
                   c.description                 AS description,
                   -- 特別な取り扱い（US05）。**記録しても見えなければ確認できない**
                   c.hazardous_class             AS hazardousClass,
                   c.un_number                   AS unNumber,
                   c.proper_shipping_name        AS properShippingName,
                   c.min_temperature             AS minTemperature,
                   c.max_temperature             AS maxTemperature,
                   c.temperature_unit            AS temperatureUnit,
                   c.consignee_name              AS consigneeName,
                   c.consignee_address           AS consigneeAddress,
                   c.consignee_email             AS consigneeEmail
              FROM cargo c
              JOIN shipper s ON s.id = c.shipper_id
            """;

    @Select("""
            <script>
            """ + SELECT_ROW + """
            <where>
              <!-- **荷主の絞り込みは利用者が外せない条件である**（US34）。
                   読み出してから画面側で捨てると、ページングの件数が合わず
                   2 ページ目に他社の予約が現れる -->
              <if test="criteria.shipperId != null">
                AND c.shipper_id = #{criteria.shipperId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
              </if>
              <if test="criteria.origin != null and criteria.origin != ''">
                AND c.origin_unlocode = #{criteria.origin}
              </if>
              <if test="criteria.destination != null and criteria.destination != ''">
                AND c.destination_unlocode = #{criteria.destination}
              </if>
              <if test="criteria.status != null and criteria.status != ''">
                AND c.booking_status = #{criteria.status}
              </if>
              <if test="criteria.trackingNumber != null and criteria.trackingNumber != ''">
                <!-- 追跡番号は部分一致・大小文字を問わない（IT6 レビュー H9）。
                     電話で読み上げられる番号は聞き取り誤りが起きやすく、
                     全桁が正確に伝わる前提を置けない。
                     UPPER で包むのは PostgreSQL・H2 の双方で解釈できるためである
                     （ILIKE は H2 の互換モードで挙動が揃わない） -->
                AND UPPER(c.tracking_number) LIKE '%' || UPPER(#{criteria.trackingNumber}) || '%'
              </if>
            </where>
            ORDER BY c.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BookingQueryRow> search(
            @Param("criteria") BookingSearchCriteria criteria,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 絞り込み後の総件数。ページ送りの総ページ数に使う。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM cargo c
            <where>
              <!-- **荷主の絞り込みは利用者が外せない条件である**（US34）。
                   読み出してから画面側で捨てると、ページングの件数が合わず
                   2 ページ目に他社の予約が現れる -->
              <if test="criteria.shipperId != null">
                AND c.shipper_id = #{criteria.shipperId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
              </if>
              <if test="criteria.origin != null and criteria.origin != ''">
                AND c.origin_unlocode = #{criteria.origin}
              </if>
              <if test="criteria.destination != null and criteria.destination != ''">
                AND c.destination_unlocode = #{criteria.destination}
              </if>
              <if test="criteria.status != null and criteria.status != ''">
                AND c.booking_status = #{criteria.status}
              </if>
              <if test="criteria.trackingNumber != null and criteria.trackingNumber != ''">
                <!-- 追跡番号は部分一致・大小文字を問わない（IT6 レビュー H9）。
                     電話で読み上げられる番号は聞き取り誤りが起きやすく、
                     全桁が正確に伝わる前提を置けない。
                     UPPER で包むのは PostgreSQL・H2 の双方で解釈できるためである
                     （ILIKE は H2 の互換モードで挙動が揃わない） -->
                AND UPPER(c.tracking_number) LIKE '%' || UPPER(#{criteria.trackingNumber}) || '%'
              </if>
            </where>
            </script>
            """)
    long count(@Param("criteria") BookingSearchCriteria criteria);

    /**
     * 経路割り当て待ち。**並び順は希望期限の昇順**（`ui_design.md`）。
     *
     * <p>期限が同じ予約の順序を安定させるため、予約 ID を第 2 キーに置く。
     * **一意なキーを最後に置かないと、ページ送りで同じ行が 2 度出たり消えたりする。**
     */
    @Select(SELECT_ROW + """
             WHERE c.booking_status = 'ROUTE_PROPOSED'
               AND c.routing_status = 'NOT_ROUTED'
             ORDER BY c.arrival_deadline, c.booking_id
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<BookingQueryRow> findAwaitingRouting(
            @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM cargo c
             WHERE c.booking_status = 'ROUTE_PROPOSED'
               AND c.routing_status = 'NOT_ROUTED'
            """)
    long countAwaitingRouting();

    /**
     * 追跡番号発行待ち（US14）。**並び順は希望期限の昇順**。
     *
     * <p><strong>状態だけでなく追跡番号の有無も見る。</strong> 状態だけで絞ると、
     * 発行に失敗して状態が戻った予約を取りこぼす余地が残る。
     */
    @Select(SELECT_ROW + """
             WHERE c.booking_status = 'CONFIRMED'
               AND c.tracking_number IS NULL
             ORDER BY c.arrival_deadline, c.booking_id
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<BookingQueryRow> findAwaitingTracking(
            @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM cargo c
             WHERE c.booking_status = 'CONFIRMED'
               AND c.tracking_number IS NULL
            """)
    long countAwaitingTracking();

    /**
     * 追跡中の貨物（US17 の作業対象）。
     *
     * <p><strong>追跡管理者が状態を手で更新するには、まず対象の追跡番号に
     * たどり着けなければならない。</strong> 発行待ち一覧は発行した時点でその予約が
     * 消えるため、発行後の貨物へ画面から到達する手段が無かった。
     * 追跡番号を覚えている追跡管理者はいない。
     *
     * <p>並び順は<strong>希望期限の昇順</strong>である。朝に見るのは
     * 「どれが一番切羽詰まっているか」であり、予約 ID 順では役に立たない。
     */
    @Select(SELECT_ROW + """
             WHERE c.tracking_number IS NOT NULL
               AND c.booking_status IN ('TRACKING_ISSUED', 'IN_TRANSIT')
             ORDER BY c.arrival_deadline, c.booking_id
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<BookingQueryRow> findInTransit(
            @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM cargo c
             WHERE c.tracking_number IS NOT NULL
               AND c.booking_status IN ('TRACKING_ISSUED', 'IN_TRANSIT')
            """)
    long countInTransit();

    /**
     * 経路が確定していて、まだ荷主に通知していない予約（US12 の作業入口）。
     *
     * <p><strong>営業には経路が確定したことが伝わらない。</strong> 経路を割り当てるのは
     * 経路設計者であり、通知すべき予約を探すには予約を 1 件ずつ開いて通知履歴を見るしかなかった。
     * <strong>「送ったつもり」を検知するという US12 の目的を、運用でいちばん壊す形である。</strong>
     *
     * <p>並び順は<strong>希望期限の昇順</strong>。朝に見るのは切羽詰まった順である。
     */
    @Select(SELECT_ROW + """
             WHERE c.routing_status = 'ROUTED'
               AND NOT EXISTS (
                     SELECT 1 FROM booking_notification n
                      WHERE n.booking_id = c.booking_id
                        AND n.notification_type = 'ROUTE_CONFIRMED')
             ORDER BY c.arrival_deadline, c.booking_id
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<BookingQueryRow> findAwaitingNotification(
            @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM cargo c
             WHERE c.routing_status = 'ROUTED'
               AND NOT EXISTS (
                     SELECT 1 FROM booking_notification n
                      WHERE n.booking_id = c.booking_id
                        AND n.notification_type = 'ROUTE_CONFIRMED')
            """)
    long countAwaitingNotification();

    @Select(SELECT_ROW + """
             WHERE c.booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    BookingQueryRow findByBookingId(@Param("bookingId") UUID bookingId);

    /**
     * 経路設計の対象になる予約を 1 件読む（ACL アダプタ用）。
     *
     * <p><strong>「読める」条件と「割り当てられる」条件を分ける。</strong>
     * ここは引き渡し済み（{@code ROUTE_PROPOSED}）までを条件とし、
     * 経路が割り当て済みでも読める。<strong>確定した後に画面が 404 になると、
     * 何を選んだのかを後から確認できなくなる。</strong>
     *
     * <p>割り当ての可否は集約（{@code Cargo.assignItinerary}）が判断する。
     * 読み取りの条件で兼ねると、読めることと変えられることが同じになる。
     */
    @Select("""
            SELECT c.origin_unlocode      AS originUnlocode,
                   c.destination_unlocode AS destinationUnlocode,
                   c.arrival_deadline     AS arrivalDeadline,
                   c.cargo_type           AS cargoType,
                   c.weight               AS weight,
                   s.name                 AS shipperName
              FROM cargo c
              JOIN shipper s ON s.id = c.shipper_id
             WHERE c.booking_id = #{bookingId}
               AND c.booking_status = 'ROUTE_PROPOSED'
            """)
    RoutableBookingRow findRoutable(@Param("bookingId") UUID bookingId);

    /**
     * 確定した旅程の区間（US11。予約詳細で読む）。
     *
     * <p><strong>ORDER BY seq_number を外さない。</strong> 順序が崩れると、
     * どの港を経由するのかが読めなくなる。
     */
    @Select("""
            SELECT l.voyage_number AS voyageNumber,
                   l.load_location_unlocode   AS loadLocation,
                   l.unload_location_unlocode AS unloadLocation,
                   l.load_time   AS loadTime,
                   l.unload_time AS unloadTime
              FROM leg l
              JOIN cargo c ON c.id = l.cargo_id
             WHERE c.booking_id = #{bookingId}
             ORDER BY l.seq_number
            """)
    List<ItineraryLegRow> findItinerary(@Param("bookingId") UUID bookingId);
}
