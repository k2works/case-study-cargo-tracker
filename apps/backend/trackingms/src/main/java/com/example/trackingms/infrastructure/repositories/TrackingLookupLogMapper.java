package com.example.trackingms.infrastructure.repositories;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 公開照会の記録（{@code tracking_lookup_log}）。 */
@Mapper
public interface TrackingLookupLogMapper {

    @Insert("""
            INSERT INTO tracking_lookup_log (tracking_number, client_ip, user_agent, found)
            VALUES (#{trackingNumber}, #{clientIp}, #{userAgent}, #{found})
            """)
    void insert(@Param("trackingNumber") String trackingNumber,
            @Param("clientIp") String clientIp, @Param("userAgent") String userAgent,
            @Param("found") boolean found);

    /**
     * ある時刻より後の照会の件数（[ADR-024] 決定 6）。
     *
     * <p>総当たりを見つける材料である。<strong>見つからなかった照会も数える</strong>
     * ——むしろそちらが手がかりになる。
     */
    @Select("""
            SELECT COUNT(*) FROM tracking_lookup_log
             WHERE client_ip = #{clientIp} AND looked_up_at >= #{since}
            """)
    int countSince(@Param("clientIp") String clientIp, @Param("since") Instant since);
}
