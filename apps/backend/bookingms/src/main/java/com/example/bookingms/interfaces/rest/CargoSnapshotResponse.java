package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.aggregates.Cargo;
import java.util.List;

/**
 * 荷役の照合に要る貨物の情報だけを返す（[ADR-023] 決定 2・US15-1）。
 *
 * <p><strong>予約の全部を返さない。</strong>返すほど handlingms が Booking の言葉に縛られ、
 * こちらの項目を変えるたびに向こうが壊れる。返すのは「作業場所を照らすのに要るもの」
 * ——出発港・目的港・旅程の区間だけである。
 *
 * <p>荷主・貨物の内容・金額は返さない。荷役作業員に使い道が無く、渡せば渡すほど
 * <strong>漏れたときの範囲が広がる</strong>。
 *
 * @param bookingId 予約番号。荷役の記録はこれで他サービスと突き合わせる
 * @param originUnLocode 出発港
 * @param destinationUnLocode 目的港
 * @param legs 旅程の区間。経路がまだ決まっていなければ空
 */
public record CargoSnapshotResponse(String bookingId, String originUnLocode,
        String destinationUnLocode, List<LegSnapshotResponse> legs) {

    public CargoSnapshotResponse {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    /**
     * 旅程の区間 1 本。
     *
     * <p>日時も地点名も返さない。照合に使うのは港のコードだけである。
     *
     * @param voyageNumber 航海番号
     * @param loadUnLocode 積込港
     * @param unloadUnLocode 荷降港
     */
    public record LegSnapshotResponse(String voyageNumber, String loadUnLocode,
            String unloadUnLocode) {
    }

    public static CargoSnapshotResponse from(Cargo cargo) {
        return new CargoSnapshotResponse(
                cargo.bookingId().map(BookingId::value).orElse(null),
                cargo.routeSpecification().origin().unLocode(),
                cargo.routeSpecification().destination().unLocode(),
                cargo.itinerary()
                        .map(itinerary -> itinerary.legs().stream()
                                .map(leg -> new LegSnapshotResponse(leg.voyageNumber().value(),
                                        leg.loadLocation().unLocode(),
                                        leg.unloadLocation().unLocode()))
                                .toList())
                        .orElseGet(List::of));
    }
}
