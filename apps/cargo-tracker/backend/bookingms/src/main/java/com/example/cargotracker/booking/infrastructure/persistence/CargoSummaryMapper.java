package com.example.cargotracker.booking.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 貨物予約の投影テーブル（data-model.md「booking_read_db」）。 */
@Mapper
public interface CargoSummaryMapper {

    /**
     * 予約番号は投影側で採番する。集約で MAX+1 しない（data-model.md）。
     *
     * <p>日付は「いつ受け付けたか」を読めるように入れ、連番は全体で一意にする。
     * 日ごとの連番にすると、日をまたぐ境目で衝突を避ける仕掛けが要る。</p>
     */
    @Select("SELECT 'B-' || to_char(#{bookedOn}::date, 'YYYY-MMDD') || '-' "
            + "|| lpad(nextval('booking_number_seq')::text, 4, '0')")
    String nextBookingNumber(@Param("bookedOn") LocalDate bookedOn);

    int insert(CargoSummaryRow row);

    CargoSummaryRow findById(@Param("bookingId") String bookingId);

    /**
     * 一覧（S20）。既定では精算済とキャンセルを外し、到着期限が近い順に並べる
     * （ui_design.md「一覧の既定条件」）。終わった予約が混ざると、一覧全体が
     * 「今日やること」として信用されなくなる。
     */
    List<CargoSummaryRow> findAll(@Param("includeFinished") boolean includeFinished,
            @Param("limit") int limit, @Param("offset") int offset);

    int countAll(@Param("includeFinished") boolean includeFinished);

    /**
     * 一覧の既定条件を検査するためだけの更新。
     *
     * <p>本来 {@code booking_status} は集約のイベントだけが書く。ここで直に更新するのは、
     * 精算まで到達する経路（US23・IT14）がまだ無く、「終了したものを既定で外す」ことを
     * 確かめられないため。<b>本番の経路では使わない。</b></p>
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE cargo_summary SET booking_status = 'SETTLED' WHERE booking_id = #{bookingId}")
    int markSettledForTest(@Param("bookingId") String bookingId);

    /** 状態ごとの件数（S02 の「今日の作業」）。仮受付は引き渡し待ちを意味する。 */
    @Select("SELECT count(*) FROM cargo_summary WHERE booking_status = #{bookingStatus}")
    int countByStatus(@Param("bookingStatus") String bookingStatus);

    /** 投影の行。shipper_name は鍵破棄後に null になる（ADR-0003）。 */
    record CargoSummaryRow(
            String bookingId,
            String bookingNumber,
            String shipperId,
            String shipperName,
            String trackingNumber,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            int quantity,
            String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC,
            String bookingStatus,
            String routingStatus,
            Instant bookedAt,
            Instant projectedAt,
            String lastEventId) {
    }
}
