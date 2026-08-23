package com.example.trackingms.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 荷主へ通知した事実（{@code tracking_notice}）。 */
@Mapper
public interface TrackingNoticeMapper {

    @Insert("""
            INSERT INTO tracking_notice (tracking_number, message, noticed_at)
            VALUES (#{trackingNumber}, #{message}, #{noticedAt})
            """)
    void insert(@Param("trackingNumber") String trackingNumber,
            @Param("message") String message, @Param("noticedAt") Instant noticedAt);

    /**
     * 新しい順に返す。
     *
     * <p>経過（古い順）とは逆である——お知らせは「いま何が起きているか」を先に読む。
     */
    @Select("""
            SELECT message, noticed_at FROM tracking_notice
             WHERE tracking_number = #{trackingNumber}
             ORDER BY noticed_at DESC, id DESC
             LIMIT #{limit}
            """)
    @Results({@Result(column = "noticed_at", property = "noticedAt")})
    List<TrackingNoticeRecord> findByTrackingNumber(
            @Param("trackingNumber") String trackingNumber, @Param("limit") int limit);
}
