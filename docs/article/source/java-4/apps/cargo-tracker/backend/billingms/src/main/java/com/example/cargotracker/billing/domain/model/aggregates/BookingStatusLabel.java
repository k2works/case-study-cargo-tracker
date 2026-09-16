package com.example.cargotracker.billing.domain.model.aggregates;

/**
 * 予約の状態の呼び名（キャンセル料の明細に出す）。
 *
 * <p><b>billingms は bookingms の型に依存できない</b>（BC の独立性）ので、
 * 契約イベントが運ぶ名前を業務の言葉に直す表をここに持つ。<b>知らない名前は
 * そのまま出す</b>——落とすと、明細から「どの状態でのキャンセルか」が消える。</p>
 *
 * <p><b>金額の判断はしない。</b> 料率は {@code RateTable} が持ち、そちらは
 * 知らない状態を断る（黙って 0 円にしない）。ここは表示だけである。</p>
 */
final class BookingStatusLabel {

    private BookingStatusLabel() {
    }

    static String of(String statusAtCancel) {
        if (statusAtCancel == null) {
            return "不明";
        }
        return switch (statusAtCancel) {
            case "PRELIMINARY" -> "仮受付";
            case "ROUTE_PROPOSED" -> "経路提案中";
            case "ROUTE_NOTIFIED" -> "経路通知済";
            case "CONFIRMED" -> "予約確定";
            case "TRACKING_ISSUED" -> "追跡番号発行済";
            case "IN_TRANSIT" -> "輸送中";
            default -> statusAtCancel;
        };
    }
}
