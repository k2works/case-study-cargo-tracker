package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.BillableCargoFinder;
import com.example.bookingms.application.port.BillableCargo;
import java.util.List;
import java.util.Optional;

/**
 * 料金算出の対象を引く（US21・[ADR-027] 決定 7）。
 *
 * <p>絞り（引取済・キャンセル済み）と並び（引取が終わった順）は SQL が持つ。
 */
public class MyBatisBillableCargoFinder implements BillableCargoFinder {

    private final BillableCargoMapper mapper;

    public MyBatisBillableCargoFinder(BillableCargoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<BillableCargo> findBillable(String bookingId) {
        return Optional.ofNullable(mapper.selectByBookingId(bookingId))
                .map(BillableCargoRecord::toBillableCargo);
    }

    @Override
    public List<BillableCargo> findAllBillable() {
        return mapper.selectAllBillable().stream()
                .map(BillableCargoRecord::toBillableCargo)
                .toList();
    }
}
