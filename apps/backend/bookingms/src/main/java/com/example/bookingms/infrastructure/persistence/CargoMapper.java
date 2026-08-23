package com.example.bookingms.infrastructure.persistence;

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
public interface CargoMapper {

    /**
     * 一覧・詳細の共通列。
     *
     * <p>地点は名称まで取る。UN/LOCODE だけを返すと、画面が 5 文字のコードから地点名を
     * 引き直すことになり、その対応表がフロントとサーバの 2 箇所に増える。
     */
    String COLUMNS = """
            c.id, c.booking_id, c.shipper_id, c.booking_status, c.transport_status,
            c.routing_status, c.cargo_type, c.weight_kg, c.quantity, c.description,
            c.length_cm, c.width_cm, c.height_cm,
            c.spec_origin_unlocode, o.name AS spec_origin_name,
            c.spec_destination_unlocode, d.name AS spec_destination_name,
            c.spec_arrival_deadline, c.spec_departure_date,
            c.hazardous_class, c.un_number, c.proper_shipping_name,
            c.temp_min, c.temp_max, c.temp_unit,
            c.route_notified_at, c.route_notified_by, c.tracking_number,
            s.name AS shipper_name
            """;

    String JOINS = """
            FROM cargo c
            JOIN shipper s ON s.id = c.shipper_id
            JOIN location o ON o.unlocode = c.spec_origin_unlocode
            JOIN location d ON d.unlocode = c.spec_destination_unlocode
            """;

    /**
     * 予約番号は DB の DEFAULT が組み立てる（ADR-011）。
     *
     * <p>採番した番号を読み戻すため、生成された主キーを受け取ってから改めて 1 行取る。
     * {@code RETURNING} は PostgreSQL 固有で、H2 では解釈できない。
     */
    @Insert("""
            INSERT INTO cargo (
                shipper_id, booking_status, transport_status, routing_status, cargo_type,
                weight_kg, quantity, description, length_cm, width_cm, height_cm,
                spec_origin_unlocode, spec_destination_unlocode,
                spec_arrival_deadline, spec_departure_date,
                hazardous_class, un_number, proper_shipping_name,
                temp_min, temp_max, temp_unit)
            VALUES (
                #{shipperId}, #{bookingStatus}, #{transportStatus}, #{routingStatus}, #{cargoType},
                #{weightKg}, #{quantity}, #{description}, #{lengthCm}, #{widthCm}, #{heightCm},
                #{specOriginUnlocode}, #{specDestinationUnlocode},
                #{specArrivalDeadline}, #{specDepartureDate},
                #{hazardousClass}, #{unNumber}, #{properShippingName},
                #{tempMin}, #{tempMax}, #{tempUnit})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(CargoRecord row);

    /**
     * 既にある予約を書き換える。
     *
     * <p>予約番号（{@code booking_id}）は書き換えない。番号は 5 サービスが論理参照するキーであり、
     * 更新のたびに変わると、他サービスが持つ参照が黙って外れる（ADR-011）。
     */
    @Update("""
            UPDATE cargo
               SET shipper_id = #{shipperId},
                   booking_status = #{bookingStatus},
                   transport_status = #{transportStatus},
                   routing_status = #{routingStatus},
                   cargo_type = #{cargoType},
                   weight_kg = #{weightKg},
                   quantity = #{quantity},
                   description = #{description},
                   length_cm = #{lengthCm},
                   width_cm = #{widthCm},
                   height_cm = #{heightCm},
                   spec_origin_unlocode = #{specOriginUnlocode},
                   spec_destination_unlocode = #{specDestinationUnlocode},
                   spec_arrival_deadline = #{specArrivalDeadline},
                   spec_departure_date = #{specDepartureDate},
                   hazardous_class = #{hazardousClass},
                   un_number = #{unNumber},
                   proper_shipping_name = #{properShippingName},
                   temp_min = #{tempMin},
                   temp_max = #{tempMax},
                   temp_unit = #{tempUnit},
                   route_notified_at = #{routeNotifiedAt},
                   route_notified_by = #{routeNotifiedBy},
                   tracking_number = #{trackingNumber},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    void update(CargoRecord row);

    @Select("SELECT " + COLUMNS + JOINS + " WHERE c.id = #{id}")
    @Results(id = "cargoResult", value = {
        @Result(column = "booking_id", property = "bookingId"),
        @Result(column = "shipper_id", property = "shipperId"),
        @Result(column = "booking_status", property = "bookingStatus"),
        @Result(column = "transport_status", property = "transportStatus"),
        @Result(column = "routing_status", property = "routingStatus"),
        @Result(column = "cargo_type", property = "cargoType"),
        @Result(column = "weight_kg", property = "weightKg"),
        @Result(column = "length_cm", property = "lengthCm"),
        @Result(column = "width_cm", property = "widthCm"),
        @Result(column = "height_cm", property = "heightCm"),
        @Result(column = "spec_origin_unlocode", property = "specOriginUnlocode"),
        @Result(column = "spec_origin_name", property = "specOriginName"),
        @Result(column = "spec_destination_unlocode", property = "specDestinationUnlocode"),
        @Result(column = "spec_destination_name", property = "specDestinationName"),
        @Result(column = "spec_arrival_deadline", property = "specArrivalDeadline"),
        @Result(column = "spec_departure_date", property = "specDepartureDate"),
        @Result(column = "hazardous_class", property = "hazardousClass"),
        @Result(column = "un_number", property = "unNumber"),
        @Result(column = "proper_shipping_name", property = "properShippingName"),
        @Result(column = "temp_min", property = "tempMin"),
        @Result(column = "temp_max", property = "tempMax"),
        @Result(column = "temp_unit", property = "tempUnit"),
        @Result(column = "route_notified_at", property = "routeNotifiedAt"),
        @Result(column = "route_notified_by", property = "routeNotifiedBy"),
        @Result(column = "tracking_number", property = "trackingNumber"),
        @Result(column = "shipper_name", property = "shipperName")
    })
    CargoRecord findById(@Param("id") Long id);

    /**
     * 追跡番号を採番する（US14-2・[ADR-011] と同じ形）。
     *
     * <p><strong>組み立てはここに置く。</strong>アプリ側で文字列を作ると、別の経路
     * （移行・運用スクリプト）が違う形式を発行できてしまい、サービスをまたいだ照合が壊れる。
     */
    @Select("""
            SELECT 'TRK-' || #{businessDate} || '-'
                   || LPAD(CAST(NEXTVAL('tracking_number_seq') AS VARCHAR), 4, '0')
            """)
    String nextTrackingNumber(@Param("businessDate") String businessDate);

    /**
     * 一覧。新しい順に返し、件数の上限を必ず置く。
     *
     * <p>絞り込みの有無は動的 SQL で分ける。`#{cargoType} IS NULL` のように書くと
     * PostgreSQL がパラメータの型を決められず落ちる（H2 では通るため気づきにくい）。
     */
    @Select("""
            <script>
            SELECT c.id, c.booking_id, c.shipper_id, c.booking_status, c.transport_status,
                   c.routing_status, c.cargo_type, c.weight_kg, c.quantity, c.description,
                   c.length_cm, c.width_cm, c.height_cm,
                   c.spec_origin_unlocode, o.name AS spec_origin_name,
                   c.spec_destination_unlocode, d.name AS spec_destination_name,
                   c.spec_arrival_deadline, c.spec_departure_date,
                   c.hazardous_class, c.un_number, c.proper_shipping_name,
                   c.temp_min, c.temp_max, c.temp_unit,
                   c.route_notified_at, c.route_notified_by, c.tracking_number,
                   s.name AS shipper_name
            FROM cargo c
            JOIN shipper s ON s.id = c.shipper_id
            JOIN location o ON o.unlocode = c.spec_origin_unlocode
            JOIN location d ON d.unlocode = c.spec_destination_unlocode
            <where>
                <if test="cargoType != null">c.cargo_type = #{cargoType}</if>
                <if test="keyword != null">
                    AND (LOWER(c.booking_id) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                         OR LOWER(s.name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
                </if>
                <if test="routingStatuses != null">
                    AND c.routing_status IN
                    <foreach item="status" collection="routingStatuses"
                             open="(" separator="," close=")">#{status}</foreach>
                </if>
                <if test="bookingStatus != null">AND c.booking_status = #{bookingStatus}</if>
            </where>
            ORDER BY c.id DESC
            LIMIT #{limit}
            </script>
            """)
    @Results(id = "cargoList", value = {
        @Result(column = "booking_id", property = "bookingId"),
        @Result(column = "shipper_id", property = "shipperId"),
        @Result(column = "booking_status", property = "bookingStatus"),
        @Result(column = "transport_status", property = "transportStatus"),
        @Result(column = "routing_status", property = "routingStatus"),
        @Result(column = "cargo_type", property = "cargoType"),
        @Result(column = "weight_kg", property = "weightKg"),
        @Result(column = "length_cm", property = "lengthCm"),
        @Result(column = "width_cm", property = "widthCm"),
        @Result(column = "height_cm", property = "heightCm"),
        @Result(column = "spec_origin_unlocode", property = "specOriginUnlocode"),
        @Result(column = "spec_origin_name", property = "specOriginName"),
        @Result(column = "spec_destination_unlocode", property = "specDestinationUnlocode"),
        @Result(column = "spec_destination_name", property = "specDestinationName"),
        @Result(column = "spec_arrival_deadline", property = "specArrivalDeadline"),
        @Result(column = "spec_departure_date", property = "specDepartureDate"),
        @Result(column = "hazardous_class", property = "hazardousClass"),
        @Result(column = "un_number", property = "unNumber"),
        @Result(column = "proper_shipping_name", property = "properShippingName"),
        @Result(column = "temp_min", property = "tempMin"),
        @Result(column = "temp_max", property = "tempMax"),
        @Result(column = "temp_unit", property = "tempUnit"),
        @Result(column = "route_notified_at", property = "routeNotifiedAt"),
        @Result(column = "route_notified_by", property = "routeNotifiedBy"),
        @Result(column = "tracking_number", property = "trackingNumber"),
        @Result(column = "shipper_name", property = "shipperName")
    })
    List<CargoRecord> search(
            @Param("cargoType") String cargoType,
            @Param("keyword") String keyword,
            @Param("routingStatuses") List<String> routingStatuses,
            @Param("bookingStatus") String bookingStatus,
            @Param("limit") int limit);

    /** 絞り込み条件に合う総件数。上限で切った一覧が全体の何件中かを示すために要る。 */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cargo c
            JOIN shipper s ON s.id = c.shipper_id
            <where>
                <if test="cargoType != null">c.cargo_type = #{cargoType}</if>
                <if test="keyword != null">
                    AND (LOWER(c.booking_id) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                         OR LOWER(s.name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
                </if>
                <if test="routingStatuses != null">
                    AND c.routing_status IN
                    <foreach item="status" collection="routingStatuses"
                             open="(" separator="," close=")">#{status}</foreach>
                </if>
                <if test="bookingStatus != null">AND c.booking_status = #{bookingStatus}</if>
            </where>
            </script>
            """)
    long count(@Param("cargoType") String cargoType, @Param("keyword") String keyword,
            @Param("routingStatuses") List<String> routingStatuses,
            @Param("bookingStatus") String bookingStatus);

    /** 予約番号から 1 件。画面の URL に出るのは予約番号であり、内部の id ではない。 */
    @Select("SELECT " + COLUMNS + JOINS + " WHERE c.booking_id = #{bookingId}")
    @ResultMap("cargoList")
    CargoRecord findByBookingId(@Param("bookingId") String bookingId);

    /**
     * 追跡番号から 1 件（US15-1）。
     *
     * <p>荷役作業員は予約番号を知らない。手元にあるのは貨物に貼られた追跡番号である。
     */
    @Select("SELECT " + COLUMNS + JOINS + " WHERE c.tracking_number = #{trackingNumber}")
    @ResultMap("cargoList")
    CargoRecord findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
