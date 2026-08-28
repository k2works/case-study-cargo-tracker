package com.example.trackingms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

/** 追跡の出来事（{@code tracking_handling_event}）。 */
@Mapper
public interface TrackingEventMapper {

    @Insert("""
            INSERT INTO tracking_handling_event (
                tracking_number, tracking_status, location_unlocode, occurred_at, source)
            VALUES (#{trackingNumber}, #{trackingStatus}, #{locationUnlocode},
                    #{occurredAt}, #{source})
            """)
    void insert(TrackingEventRecord row);

    /**
     * 1 つの貨物の経過を、起きた順に返す。
     *
     * <p><strong>古い順に並べる。</strong>荷主は起きた順に読む。新しい順にすると
     * 「受領の前に積込がある」ように見える。
     *
     * <p>地点は名称まで取る。UN/LOCODE だけを返すと、画面が 5 文字のコードから引き直す
     * ことになり、その対応表がフロントとサーバの 2 箇所に増える。
     *
     * @param limit 返す件数の上限。上限が無いと、件数が増えた日に照会が開かなくなる
     */
    @Select("""
            SELECT e.id, e.tracking_number, e.tracking_status,
                   e.location_unlocode, l.name AS location_name,
                   e.occurred_at, e.source
              FROM tracking_handling_event e
              JOIN location l ON l.unlocode = e.location_unlocode
             WHERE e.tracking_number = #{trackingNumber}
             ORDER BY e.occurred_at, e.id
             LIMIT #{limit}
            """)
    @Result(column = "tracking_number", property = "trackingNumber")
    @Result(column = "tracking_status", property = "trackingStatus")
    @Result(column = "location_unlocode", property = "locationUnlocode")
    @Result(column = "location_name", property = "locationName")
    @Result(column = "occurred_at", property = "occurredAt")
    List<TrackingEventRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber,
            @Param("limit") int limit);
}
