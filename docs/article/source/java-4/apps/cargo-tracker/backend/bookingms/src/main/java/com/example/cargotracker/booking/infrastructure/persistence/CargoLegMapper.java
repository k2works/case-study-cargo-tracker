package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 確定した旅程の区間（US09）。 */
@Mapper
public interface CargoLegMapper {

    /**
     * 全行を入れ替える（data-model.md）。
     *
     * <p>足すだけにすると、経路を設計し直した予約に古い区間が残り、旅程が二重に
     * 見える。短くなった旅程では、行かないはずの港が残る。</p>
     */
    int deleteByBooking(@Param("bookingId") String bookingId);

    int insert(CargoLegRow row);

    /** 積む順。並び順そのものが業務の意味を持つ。 */
    List<CargoLegRow> findByBooking(@Param("bookingId") String bookingId);

    /**
     * その航海で経路を組んだ予約（S34 / US24）。予約番号の順に返す。
     *
     * <p><b>1 予約 1 行にする。</b> 同じ航海を 2 区間で使う旅程があるので、区間ごとに
     * 返すと同じ予約が 2 度数えられ、件数が実際より多くなる。</p>
     */
    List<AffectedBookingRow> findBookingsByVoyage(@Param("voyageNumber") String voyageNumber);

    /** 巻き込む予約 1 件。状態は cargo_summary から採る。 */
    record AffectedBookingRow(
            String bookingId,
            String bookingNumber,
            String bookingStatus,
            String routingStatus) {
    }

    record CargoLegRow(
            String bookingId,
            int legSeq,
            String voyageNumber,
            String loadUnlocode,
            String unloadUnlocode,
            Instant loadAt,
            Instant unloadAt) {
    }
}
