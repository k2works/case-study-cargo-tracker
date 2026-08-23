package com.example.handlingms.domain.model;

import java.util.List;

/**
 * 貨物の写し（[ADR-023] 決定 2）。ACL 経由で bookingms の REST から取る。
 *
 * <p><strong>作業場所の照合はここが答える。</strong>判定を呼び出し側やテストに書き直すと、
 * 本番が間違っていても検査だけが正しく、素通りする。
 *
 * @param bookingId 予約番号
 * @param originUnLocode 出発港
 * @param destinationUnLocode 目的港
 * @param legs 旅程の区間。経路がまだ決まっていなければ空
 */
public record CargoSnapshot(String bookingId, String originUnLocode, String destinationUnLocode,
        List<LegSnapshot> legs) {

    public CargoSnapshot {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    public static CargoSnapshot of(String bookingId, String originUnLocode,
            String destinationUnLocode, List<LegSnapshot> legs) {
        require(bookingId, "予約番号");
        require(originUnLocode, "出発地");
        require(destinationUnLocode, "目的地");
        return new CargoSnapshot(bookingId, originUnLocode, destinationUnLocode, legs);
    }

    private static void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "は必須です");
        }
    }

    /**
     * その作業場所が予定と違うか（[ADR-023] 決定 3）。
     *
     * <p><strong>予定外でも作業は拒まない。</strong>現場ではすでに終わっており、拒むと
     * 実際に起きたことがどこにも残らない。ここが答えるのは「予定外だったか」だけで、
     * 記録するかどうかは集約が決める。
     *
     * <p><strong>照らす相手が無いときは予定外に倒す。</strong>旅程が決まる前に船へ積んでも
     * 「予定どおり」と答えると、記録に何も残らない。
     */
    public boolean isOffRoute(HandlingType type, String unLocode) {
        return !matchesExpectedPort(type, unLocode);
    }

    private boolean matchesExpectedPort(HandlingType type, String unLocode) {
        return switch (type.expectedPort()) {
            case ORIGIN -> originUnLocode.equals(unLocode);
            case DESTINATION -> destinationUnLocode.equals(unLocode);
            case ITINERARY_LOAD -> legs.stream()
                    .anyMatch(leg -> leg.loadUnLocode().equals(unLocode));
            case ITINERARY_UNLOAD -> legs.stream()
                    .anyMatch(leg -> leg.unloadUnLocode().equals(unLocode));
        };
    }
}
