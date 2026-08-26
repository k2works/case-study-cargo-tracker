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
                .map(row -> row.toBillableCargo(
                        group(mapper.selectLegsByBookingId(bookingId))
                                .getOrDefault(bookingId, List.of())));
    }

    @Override
    public List<BillableCargo> findAllBillable() {
        List<BillableCargoRecord> rows = mapper.selectAllBillable();
        if (rows.isEmpty()) {
            return List.of();
        }
        // **区間はまとめて 1 回で引く。** 1 件ずつ引くと、対象が増えるほど
        // 問い合わせが増える（N+1）——一覧は経理担当者が毎朝開く画面である
        java.util.Map<String, List<BillableCargo.Leg>> legs =
                group(mapper.selectAllBillableLegs());

        return rows.stream()
                .map(row -> row.toBillableCargo(legs.getOrDefault(row.getBookingId(), List.of())))
                .toList();
    }

    /** 予約番号 → 旅程の区間（順序どおり）。 */
    private java.util.Map<String, List<BillableCargo.Leg>> group(List<BillableLegRecord> rows) {
        java.util.Map<String, List<BillableCargo.Leg>> legs = new java.util.LinkedHashMap<>();
        for (BillableLegRecord row : rows) {
            legs.computeIfAbsent(row.getBookingId(), key -> new java.util.ArrayList<>())
                    .add(new BillableCargo.Leg(row.getLoadRegion(), row.getUnloadRegion()));
        }
        return legs;
    }
}
