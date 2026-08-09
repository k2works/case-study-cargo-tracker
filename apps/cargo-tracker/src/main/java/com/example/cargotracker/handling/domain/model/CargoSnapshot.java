package com.example.cargotracker.handling.domain.model;

import java.util.List;

/**
 * ACL 経由で受け取った予約の写し。荷役の妥当性検証に使う。
 *
 * <p><strong>すべて素の値である。</strong> Booking の型をそのまま受け取ると、
 * 荷役モジュールが相手のドメインを参照することになる（ArchUnit ルール 4）。
 *
 * <p>これは「いま予約がどうなっているか」の写しであり、<strong>保存しない</strong>。
 * 保存すると、予約が変わったときに古い写しで誤配を判定することになる。
 *
 * @param bookingId     予約 ID
 * @param origin        予約の出発地（UN/LOCODE）
 * @param destination   予約の目的地（UN/LOCODE）
 * @param itineraryLegs 予定ルートの区間。経路が未割り当てなら空
 */
public record CargoSnapshot(
        String bookingId,
        String origin,
        String destination,
        String consigneeName,
        List<LegSnapshot> itineraryLegs) {

    public CargoSnapshot {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        itineraryLegs = List.copyOf(itineraryLegs == null ? List.of() : itineraryLegs);
    }

    /** 経路が割り当てられているか。 */
    public boolean isRouted() {
        return !itineraryLegs.isEmpty();
    }

    /**
     * 予定ルートの区間 1 つ分。
     *
     * @param voyageNumber   航海番号
     * @param loadLocation   積込港（UN/LOCODE）
     * @param unloadLocation 荷降港（UN/LOCODE）
     */
    public record LegSnapshot(String voyageNumber, String loadLocation, String unloadLocation) {
    }

    /**
     * この貨物に通関が要るか（US29 / C29）。
     *
     * <p><strong>「申告があるかどうか」ではない。</strong> それは手続きの有無であって、
     * 通関が要るかどうかではない。IT11 の引取の拒否は申告のある貨物にしか効かず、
     * <strong>申告を出し忘れた輸入貨物は引取が通っていた</strong>。
     * 実務では<strong>出し忘れている貨物こそ引き取らせてはいけない</strong>。
     *
     * <p>国は <strong>UN/LOCODE の先頭 2 文字</strong>である（ISO 3166-1 alpha-2）。
     * マスタを引かずに判断できる — <strong>引取の可否を決める場所で DB を 1 回増やさない</strong>。
     *
     * <p><strong>判断できないときは「要る」に倒す。</strong> 読めない港コードで
     * 「要らない」と決めると、引取の守りが黙って外れる。
     */
    public boolean requiresCustoms() {
        String from = countryOf(origin);
        String to = countryOf(destination);
        if (from == null || to == null) {
            return true;
        }
        return !from.equals(to);
    }

    /** UN/LOCODE の先頭 2 文字（国コード）。読めなければ {@code null}。 */
    private static String countryOf(String unlocode) {
        return unlocode == null || unlocode.length() < 5 ? null : unlocode.substring(0, 2);
    }
}
