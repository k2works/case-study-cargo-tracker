package com.example.handlingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HandlingActivityMapper {

    /**
     * 作業場所は名称まで取る。
     *
     * <p>UN/LOCODE だけを返すと、画面が 5 文字のコードから地点名を引き直すことになり、
     * その対応表がフロントとサーバの 2 箇所に増える。
     */
    String COLUMNS = """
            h.id, h.booking_id, h.event_type, h.event_completion_time,
            h.location_unlocode, l.name AS location_name,
            h.voyage_number, h.operator_name, h.consignee_confirmation, h.off_route
            """;

    String JOINS = """
            FROM handling_activity h
            JOIN location l ON l.unlocode = h.location_unlocode
            """;

    @Insert("""
            INSERT INTO handling_activity (
                booking_id, event_type, event_completion_time, location_unlocode,
                voyage_number, operator_name, consignee_confirmation, off_route)
            VALUES (
                #{bookingId}, #{eventType}, #{eventCompletionTime}, #{locationUnlocode},
                #{voyageNumber}, #{operatorName}, #{consigneeConfirmation}, #{offRoute})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(HandlingActivityRecord row);

    @Select("SELECT " + COLUMNS + JOINS + " WHERE h.id = #{id}")
    @Results(id = "handlingResult", value = {
        @Result(column = "booking_id", property = "bookingId"),
        @Result(column = "event_type", property = "eventType"),
        @Result(column = "event_completion_time", property = "eventCompletionTime"),
        @Result(column = "location_unlocode", property = "locationUnlocode"),
        @Result(column = "location_name", property = "locationName"),
        @Result(column = "voyage_number", property = "voyageNumber"),
        @Result(column = "operator_name", property = "operatorName"),
        @Result(column = "consignee_confirmation", property = "consigneeConfirmation"),
        @Result(column = "off_route", property = "offRoute")
    })
    HandlingActivityRecord findById(@Param("id") Long id);

    /**
     * 1 つの貨物に何が起きたかを、時系列で返す。
     *
     * <p><strong>古い順に並べる。</strong>荷役は起きた順に読むものであり、新しい順にすると
     * 「受領の前に積込がある」ように見える。
     */
    @Select("SELECT " + COLUMNS + JOINS + """
             WHERE h.booking_id = #{bookingId}
             ORDER BY h.event_completion_time, h.id
             LIMIT #{limit}
            """)
    @org.apache.ibatis.annotations.ResultMap("handlingResult")
    List<HandlingActivityRecord> findByBookingId(@Param("bookingId") String bookingId,
            @Param("limit") int limit);

    /**
     * 同じ作業がすでに記録されているか（IT8 返済枠 0.8）。
     *
     * <p><strong>絞り込みは SQL で行う。</strong>履歴を全部読んで Java で数えると、
     * 件数が増えた日に重複の検査が一覧の重さを引き継ぐ。
     */
    @Select("""
            SELECT COUNT(*) FROM handling_activity
             WHERE booking_id = #{bookingId}
               AND event_type = #{eventType}
               AND location_unlocode = #{locationUnlocode}
               AND event_completion_time = #{completionTime}
            """)
    int countSameActivity(@Param("bookingId") String bookingId,
            @Param("eventType") String eventType,
            @Param("locationUnlocode") String locationUnlocode,
            @Param("completionTime") java.time.Instant completionTime);
}
