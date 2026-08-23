package com.example.trackingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
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
            t.id, t.tracking_number, t.booking_id, t.tracking_status,
            t.origin_unlocode, o.name AS origin_name,
            t.destination_unlocode, d.name AS destination_name,
            t.arrival_deadline
            """;

    String JOINS = """
            FROM tracking_activity t
            JOIN location o ON o.unlocode = t.origin_unlocode
            JOIN location d ON d.unlocode = t.destination_unlocode
            """;

    /**
     * まだ無ければ入れる。<strong>重複は一意制約が決める</strong>。
     *
     * <p>「探してから無ければ入れる」形にすると、同じイベントが同時に 2 通届いたときに
     * 双方が「無い」と読んでから書き、後の 1 通が落ちてデッドレターへ回る。
     *
     * <p>例外を捕まえる形も使えない——PostgreSQL は制約違反でトランザクションを中断するため、
     * その後の読み出しまで落ちる。いまトランザクション境界が無いことに頼ると、後から
     * {@code @Transactional} を足した人が静かに壊すことになる。
     */
    @Insert("""
            INSERT INTO tracking_activity (
                tracking_number, booking_id, tracking_status,
                origin_unlocode, destination_unlocode, arrival_deadline)
            VALUES (
                #{trackingNumber}, #{bookingId}, #{trackingStatus},
                #{originUnlocode}, #{destinationUnlocode}, #{arrivalDeadline})
            ON CONFLICT (tracking_number) DO NOTHING
            """)
    void insertIfAbsent(TrackingActivityRecord row);

    /**
     * 追跡の状態を更新する（US15-4）。
     *
     * <p>更新するのは状態だけである。出発地・目的地・期限は追跡が始まったときに決まり、
     * 荷役では変わらない。全項目を書き戻す形にすると、イベントが運んでこない項目まで
     * 上書きすることになる。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE tracking_activity
               SET tracking_status = #{trackingStatus}, updated_at = NOW()
             WHERE tracking_number = #{trackingNumber}
            """)
    void updateStatus(@Param("trackingNumber") String trackingNumber,
            @Param("trackingStatus") String trackingStatus);

    @Select("SELECT " + COLUMNS + JOINS + " WHERE t.tracking_number = #{trackingNumber}")
    @Results(id = "trackingResult", value = {
        @Result(column = "tracking_number", property = "trackingNumber"),
        @Result(column = "booking_id", property = "bookingId"),
        @Result(column = "tracking_status", property = "trackingStatus"),
        @Result(column = "origin_unlocode", property = "originUnlocode"),
        @Result(column = "origin_name", property = "originName"),
        @Result(column = "destination_unlocode", property = "destinationUnlocode"),
        @Result(column = "destination_name", property = "destinationName"),
        @Result(column = "arrival_deadline", property = "arrivalDeadline")
    })
    TrackingActivityRecord findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
