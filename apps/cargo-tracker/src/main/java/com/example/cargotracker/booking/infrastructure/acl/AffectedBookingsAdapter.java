package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.AffectedBookings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

/**
 * {@link AffectedBookings} の実装（US25）。
 *
 * <p><strong>SQL で数える。</strong> 予約を読み込んでから絞ると、便を 1 本直すたびに
 * 全予約を組み立てることになる。
 */
@Component
public class AffectedBookingsAdapter implements AffectedBookings {

    private final AffectedBookingMapper mapper;

    public AffectedBookingsAdapter(AffectedBookingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int countByVoyageNumber(String voyageNumber) {
        return mapper.countConfirmedByVoyageNumber(voyageNumber);
    }

    /** 確定した経路に含まれる区間から予約を数えるマッパー。 */
    @Mapper
    public interface AffectedBookingMapper {

        /**
         * この航海を確定した経路に含む予約の件数。
         *
         * <p><strong>DISTINCT を外さない。</strong> 同じ予約が同じ便に 2 区間で
         * 乗ることがあり（往復・積み替え）、数えると件数が二重になる。
         */
        @Select("""
                SELECT COUNT(DISTINCT c.id)
                  FROM leg l
                  JOIN cargo c ON c.id = l.cargo_id
                 WHERE l.voyage_number = #{voyageNumber}
                """)
        int countConfirmedByVoyageNumber(@Param("voyageNumber") String voyageNumber);
    }
}
