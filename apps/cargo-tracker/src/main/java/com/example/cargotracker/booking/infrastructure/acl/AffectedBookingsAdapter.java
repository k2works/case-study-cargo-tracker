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
        return mapper.countActiveByVoyageNumber(voyageNumber);
    }

    @Override
    public java.util.List<AffectedBooking> findByVoyageNumber(String voyageNumber) {
        return mapper.findActiveByVoyageNumber(voyageNumber).stream()
                .map(row -> new AffectedBooking(
                        row.getBookingId(),
                        row.getShipperName(),
                        row.getDestination(),
                        com.example.cargotracker.booking.domain.model.BookingStatus
                                .valueOf(row.getBookingStatus()).displayName(),
                        row.getTrackingNumber() == null ? "" : row.getTrackingNumber()))
                .toList();
    }

    /** 確定した経路に含まれる区間から予約を数えるマッパー。 */
    @Mapper
    public interface AffectedBookingMapper {

        /**
         * この航海を経路に含む予約のうち、**まだ生きているもの**の件数。
         *
         * <p><strong>DISTINCT を外さない。</strong> 同じ予約が同じ便に 2 区間で
         * 乗ることがあり（往復・積み替え）、数えると件数が二重になる。
         *
         * <p><strong>キャンセル済みは数えない。</strong> 件数は「連絡が要る仕事が
         * 残っているか」の判断材料であり、キャンセル済みが混ざると
         * <strong>連絡先の無い仕事を数える</strong>ことになる。
         */
        @Select("""
                SELECT COUNT(DISTINCT c.id)
                  FROM leg l
                  JOIN cargo c ON c.id = l.cargo_id
                 WHERE l.voyage_number = #{voyageNumber}
                   AND c.booking_status <> 'CANCELLED'
                """)
        int countActiveByVoyageNumber(@Param("voyageNumber") String voyageNumber);

        /**
         * この航海を経路に含む生きている予約。
         *
         * <p><strong>DISTINCT を外さない</strong>（同じ理由）。
         * 並びは荷主名にする。**連絡は荷主単位で行う**ためである。
         */
        @Select("""
                SELECT DISTINCT CAST(c.booking_id AS VARCHAR) AS bookingId,
                       s.name                AS shipperName,
                       c.destination_unlocode AS destination,
                       c.booking_status      AS bookingStatus,
                       c.tracking_number     AS trackingNumber
                  FROM leg l
                  JOIN cargo c ON c.id = l.cargo_id
                  JOIN shipper s ON s.id = c.shipper_id
                 WHERE l.voyage_number = #{voyageNumber}
                   AND c.booking_status <> 'CANCELLED'
                 ORDER BY s.name
                """)
        java.util.List<AffectedBookingRow> findActiveByVoyageNumber(
                @Param("voyageNumber") String voyageNumber);
    }

    /** 影響する予約の 1 行（MyBatis の受け皿）。 */
    public static class AffectedBookingRow {

        private String bookingId;
        private String shipperName;
        private String destination;
        private String bookingStatus;
        private String trackingNumber;

        public String getBookingId() {
            return bookingId;
        }

        public void setBookingId(String bookingId) {
            this.bookingId = bookingId;
        }

        public String getShipperName() {
            return shipperName;
        }

        public void setShipperName(String shipperName) {
            this.shipperName = shipperName;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getBookingStatus() {
            return bookingStatus;
        }

        public void setBookingStatus(String bookingStatus) {
            this.bookingStatus = bookingStatus;
        }

        public String getTrackingNumber() {
            return trackingNumber;
        }

        public void setTrackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
        }
    }
}
