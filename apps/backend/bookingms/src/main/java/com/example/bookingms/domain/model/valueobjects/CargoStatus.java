package com.example.bookingms.domain.model.valueobjects;

/**
 * 貨物の状態。予約・輸送・経路の 3 つで 1 組の意味を持つ。
 *
 * <p>ばらばらに渡すと、復元のたびに順番を取り違える余地が残る。3 つとも同じ型
 * （文字列由来の列挙）であり、入れ替わっても型では気づけない。
 *
 * <p>いずれも空欄にしない（ADR-009）。「まだ動いていない」は値の無い状態ではなく、
 * 意味のある状態である。
 */
public record CargoStatus(
        BookingStatus booking, TransportStatus transport, RoutingStatus routing) {

    /** 仮受付。経路も配送もまだ始まっていない。 */
    public static CargoStatus preliminary() {
        return new CargoStatus(
                BookingStatus.PRELIMINARY, TransportStatus.NOT_RECEIVED, RoutingStatus.NOT_ROUTED);
    }
}
