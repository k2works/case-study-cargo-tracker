package com.example.trackingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrackingActivityMapper {

    /**
     * 地点は名称まで取る。
     *
     * <p>UN/LOCODE だけを返すと、画面が 5 文字のコードから地点名を引き直すことになり、
     * その対応表がフロントとサーバの 2 箇所に増える（bookingms と同じ形）。
     */
    String COLUMNS = """
            t.id, t.tracking_number, t.booking_id, t.transport_status,
            t.origin_unlocode, o.name AS origin_name,
            t.destination_unlocode, d.name AS destination_name,
            t.arrival_deadline
            """;

    String JOINS = """
            FROM tracking_activity t
            JOIN location o ON o.unlocode = t.origin_unlocode
            JOIN location d ON d.unlocode = t.destination_unlocode
            """;

    @Insert("""
            INSERT INTO tracking_activity (
                tracking_number, booking_id, transport_status,
                origin_unlocode, destination_unlocode, arrival_deadline)
            VALUES (
                #{trackingNumber}, #{bookingId}, #{transportStatus},
                #{originUnlocode}, #{destinationUnlocode}, #{arrivalDeadline})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(TrackingActivityRecord row);

    @Select("SELECT " + COLUMNS + JOINS + " WHERE t.tracking_number = #{trackingNumber}")
    @Results(id = "trackingResult", value = {
        @Result(column = "tracking_number", property = "trackingNumber"),
        @Result(column = "booking_id", property = "bookingId"),
        @Result(column = "transport_status", property = "transportStatus"),
        @Result(column = "origin_unlocode", property = "originUnlocode"),
        @Result(column = "origin_name", property = "originName"),
        @Result(column = "destination_unlocode", property = "destinationUnlocode"),
        @Result(column = "destination_name", property = "destinationName"),
        @Result(column = "arrival_deadline", property = "arrivalDeadline")
    })
    TrackingActivityRecord findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
