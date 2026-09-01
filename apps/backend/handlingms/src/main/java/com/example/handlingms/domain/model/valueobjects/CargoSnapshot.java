package com.example.handlingms.domain.model.valueobjects;

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
        List<LegSnapshot> legs, boolean simulated) {

    /**
     * <strong>検査はここに置く。</strong>
     *
     * <p>レコードの正準コンストラクタは公開されるため、{@code of} にだけ検査を置くと
     * <strong>それを使わなかった一箇所</strong>から素通りできる。「みんな {@code of} を
     * 使う」は規約であって仕組みではない。
     */
    public CargoSnapshot {
        require(bookingId, "予約番号");
        require(originUnLocode, "出発地");
        require(destinationUnLocode, "目的地");
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    /** 名前のある入口。検査そのものは正準コンストラクタが持つ。 */
    public static CargoSnapshot of(String bookingId, String originUnLocode,
            String destinationUnLocode, List<LegSnapshot> legs) {
        return new CargoSnapshot(bookingId, originUnLocode, destinationUnLocode, legs, false);
    }

    /** 由来を伴う入口（[ADR-030] 決定 3・TD-02）。 */
    public static CargoSnapshot of(String bookingId, String originUnLocode,
            String destinationUnLocode, List<LegSnapshot> legs, boolean simulated) {
        return new CargoSnapshot(bookingId, originUnLocode, destinationUnLocode, legs, simulated);
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
