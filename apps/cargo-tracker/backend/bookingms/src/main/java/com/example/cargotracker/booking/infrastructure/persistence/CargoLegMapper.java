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
