package com.example.bookingms.infrastructure.repositories.mybatis;

import com.example.bookingms.domain.projections.CargoLeg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 確定旅程（cargo_leg）の MyBatis Mapper（US11）。
 *
 * <p>SQL は {@code resources/mapper/CargoLegMapper.xml} で定義する。
 * 経路確定（CargoRoutedEvent）で booking_id 単位に旅程を再確定する（delete → insert）。</p>
 */
@Mapper
public interface CargoLegMapper {

    @SuppressWarnings("java:S107") // MyBatis Mapper は SQL の全カラムをパラメータに必要とするため許容
    void insert(@Param("bookingId") String bookingId,
                @Param("legSeq") int legSeq,
                @Param("voyageNumber") String voyageNumber,
                @Param("loadUnlocode") String loadUnlocode,
                @Param("unloadUnlocode") String unloadUnlocode,
                @Param("loadAt") LocalDateTime loadAt,
                @Param("unloadAt") LocalDateTime unloadAt);

    void deleteByBookingId(@Param("bookingId") String bookingId);

    List<CargoLeg> findByBookingId(@Param("bookingId") String bookingId);
}
