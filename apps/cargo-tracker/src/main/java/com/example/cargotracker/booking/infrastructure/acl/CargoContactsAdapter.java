package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.CargoContacts;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

/**
 * {@link CargoContacts} の実装（US19 / US20）。
 *
 * <p><strong>実装を Booking 側に置く。</strong> ポートを定義するのは利用側（Tracking）、
 * 実装するのは提供側（Booking）であり、越境は「Tracking が定義した契約を
 * Booking が満たす」という 1 方向だけになる（ADR-005 / ADR-012）。
 *
 * <p>境界の外に出すのは荷主の名前だけで、荷主や予約のドメインオブジェクトは渡さない。
 */
@Component
public class CargoContactsAdapter implements CargoContacts {

    private final CargoContactMapper mapper;

    public CargoContactsAdapter(CargoContactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<UUID, String> findShipperNames(List<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            // **空のリストで IN 句を組み立てない。** 構文エラーになる
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (CargoContactRow row : mapper.findShipperNames(
                bookingIds.stream().distinct().map(UUID::toString).toList())) {
            names.put(UUID.fromString(row.getBookingId()), row.getShipperName());
        }
        return names;
    }

    @Override
    public Optional<CargoSummary> findSummary(UUID bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        CargoSummaryRow row = mapper.findSummary(bookingId.toString());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new CargoSummary(
                row.getOrigin(), row.getDestination(),
                CargoType.valueOf(row.getCargoType()).displayName(),
                // **単位まで含めて渡す。** 数字だけを渡すと、
                // 受け取った側が単位を推測して書くことになる
                "%s kg".formatted(row.getWeight().stripTrailingZeros().toPlainString())));
    }

    /** 予約から荷主名を引くマッパー。 */
    @Mapper
    public interface CargoContactMapper {

        /**
         * まとめて引く（N+1 を作らない）。
         *
         * <p><strong>予約 ID は文字列で比較する。</strong> UUID の型ハンドラは
         * {@code foreach} の要素には効かず、方言によって解釈が分かれる。
         * 一覧の表示に使う読み取りであり、書き込みの経路とは別である。
         */
        @Select("""
                <script>
                SELECT CAST(c.booking_id AS VARCHAR) AS bookingId, s.name AS shipperName
                  FROM cargo c
                  JOIN shipper s ON s.id = c.shipper_id
                 WHERE CAST(c.booking_id AS VARCHAR) IN
                <foreach item="id" collection="bookingIds" open="(" separator="," close=")">
                  #{id}
                </foreach>
                </script>
                """)
        List<CargoContactRow> findShipperNames(@Param("bookingIds") List<String> bookingIds);

        /** 貨物の要約を 1 件引く（C19）。 */
        @Select("""
                SELECT c.origin_unlocode AS origin, c.destination_unlocode AS destination,
                       c.cargo_type AS cargoType, c.weight AS weight
                  FROM cargo c
                 WHERE CAST(c.booking_id AS VARCHAR) = #{bookingId}
                """)
        CargoSummaryRow findSummary(@Param("bookingId") String bookingId);
    }

    /** 引いた貨物の要約。 */
    public static class CargoSummaryRow {

        private String origin;
        private String destination;
        private String cargoType;
        private BigDecimal weight;

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getCargoType() {
            return cargoType;
        }

        public void setCargoType(String cargoType) {
            this.cargoType = cargoType;
        }

        public BigDecimal getWeight() {
            return weight;
        }

        public void setWeight(BigDecimal weight) {
            this.weight = weight;
        }
    }

    /** 引いた行。 */
    public static class CargoContactRow {

        private String bookingId;
        private String shipperName;

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
    }
}
