package com.example.cargotracker.booking.domain.model;

/**
 * 貨物の経路（状態と旅程のひと組）。
 *
 * <p><strong>状態と旅程を別々に持たない。</strong> 「割り当て済なのに区間が無い」
 * 「区間はあるが未割り当て」という組み合わせは業務上あり得ず、
 * 別々の項目にすると、その組み合わせを作れてしまう。
 *
 * @param status    経路状態
 * @param itinerary 旅程。割り当て前は {@code null}
 */
public record CargoRouting(CargoRoutingStatus status, CargoItinerary itinerary) {

    public CargoRouting {
        if (status == null) {
            throw new IllegalArgumentException("経路状態は必須です");
        }
        if (status == CargoRoutingStatus.NOT_ROUTED && itinerary != null) {
            throw new IllegalArgumentException("未割り当ての貨物は旅程を持ちません");
        }
        if (status != CargoRoutingStatus.NOT_ROUTED && itinerary == null) {
            throw new IllegalArgumentException("割り当て済の貨物には旅程が必要です");
        }
    }

    /** 経路が割り当てられていない状態。 */
    public static CargoRouting notRouted() {
        return new CargoRouting(CargoRoutingStatus.NOT_ROUTED, null);
    }

    /** 経路が割り当てられた状態（US09 / US11）。 */
    public static CargoRouting routed(CargoItinerary itinerary) {
        return new CargoRouting(CargoRoutingStatus.ROUTED, itinerary);
    }

    /** 割り当て済か。 */
    public boolean isRouted() {
        return status == CargoRoutingStatus.ROUTED;
    }
}
