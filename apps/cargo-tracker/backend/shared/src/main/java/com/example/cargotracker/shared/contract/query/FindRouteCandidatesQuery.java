package com.example.cargotracker.shared.contract.query;

import java.time.LocalDate;
import java.util.List;

/**
 * 経路候補を問い合わせる（US08）。bookingms → routingms。<b>契約クエリはこの 1 本だけ</b>
 * （architecture_backend.md「サービス越しの問い合わせ」）。
 *
 * <p><b>中身は文字列・数値・日付だけにする。</b> 識別子型（{@code VoyageNumber}）も
 * 列挙型（{@code CargoType}）も共有カーネルに置かない決まりなので、両 BC がそれぞれの
 * 型へ組み直す。契約に BC の型を載せると、片方の BC の都合で契約が動く。</p>
 *
 * <p><b>端点と期限だけでは足りない。</b> 貨物種別を渡さないと、危険物を運べない航海が
 * 候補に混ざる。除外港は条件調整（US10）、{@code departFromUnLocode} は誤配の再設計
 * （US28）で使う。</p>
 *
 * @param originUnLocode 出発地の UN/LOCODE
 * @param destinationUnLocode 目的地の UN/LOCODE
 * @param arrivalDeadline 到着期限（日付。時刻は持たない）
 * @param cargoType 貨物種別の名前（{@code GENERAL} / {@code HAZARDOUS} / {@code REEFER}）
 * @param excludeUnLocodes 通したくない港。無ければ空リスト（null も空として扱う）
 * @param departFromUnLocode 探索の起点。通常は null（出発地から探す）
 */
public record FindRouteCandidatesQuery(
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        List<String> excludeUnLocodes,
        String departFromUnLocode) {

    public FindRouteCandidatesQuery {
        excludeUnLocodes = excludeUnLocodes == null ? List.of() : List.copyOf(excludeUnLocodes);
    }
}
