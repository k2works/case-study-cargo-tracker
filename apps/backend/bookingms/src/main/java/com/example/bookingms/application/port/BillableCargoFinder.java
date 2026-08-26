package com.example.bookingms.application.port;

import java.util.List;
import java.util.Optional;

/**
 * 料金算出の対象になる予約を引く出力ポート（US21・[ADR-027] 決定 5・決定 7）。
 *
 * <p><strong>対象は引取済（{@code DELIVERED}）とキャンセル済み（{@code CANCELLED}）だけ
 * である。</strong>輸送中の予約はまだ運び終えておらず、請求する金額が決まらない。
 * <strong>絞りをここに置く</strong>——呼び出し側で絞ると、画面と API で別々の条件を
 * 持つことになる。
 *
 * <p>読み取り専用のクエリであり、集約を経由しない（CQRS のクエリ側）。
 */
public interface BillableCargoFinder {

    /** 1 件を引く。料金算出の対象でなければ空を返す。 */
    Optional<BillableCargo> findBillable(String bookingId);

    /**
     * 対象になる予約をすべて並べる。
     *
     * <p><strong>引取が終わった順に並べる。</strong>待たせている案件が上に来る
     * ——新しい順だと、いちばん待たせている荷主への請求が下に沈む。
     */
    List<BillableCargo> findAllBillable();
}
