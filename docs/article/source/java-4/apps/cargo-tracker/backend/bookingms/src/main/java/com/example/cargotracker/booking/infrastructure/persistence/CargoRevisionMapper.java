package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 予約の修正履歴（US32 §受入基準 4）。 */
@Mapper
public interface CargoRevisionMapper {

    /**
     * 1 項目分の変更を書く。
     *
     * <p>{@code ON CONFLICT DO NOTHING}。行は修正イベントから決まりきった形で導くので、
     * リプレイで同じ行が来る。連番で採ると読み直しのたびに増える。</p>
     */
    int insert(CargoRevisionRow row);

    /** 新しい修正が先。同じ修正の中は項目の並び（書いた順）を保つ。 */
    List<CargoRevisionRow> findByBooking(@Param("bookingId") String bookingId);

    record CargoRevisionRow(
            String bookingId,
            Instant updatedAt,
            String fieldLabel,
            int fieldSeq,
            String beforeValue,
            String afterValue,
            String updatedBy) {
    }
}
