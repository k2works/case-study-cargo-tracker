package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 航海が受け入れる貨物種別。
 *
 * <p><b>Booking の {@code CargoType} とは別の型</b>にする。同じ名前でも、Booking では
 * 「その貨物が何か」、Routing では「その航海が何を受け入れるか」で、値が増える理由も
 * 別になる。共有カーネルに列挙型は置かない（domain-model.md）。</p>
 */
public enum CargoType {
    /** 一般貨物。 */
    GENERAL,
    /** 危険物。 */
    HAZARDOUS,
    /** 冷凍・冷蔵貨物。 */
    REEFER;

    /**
     * 不変条件 4 の既定（空なら一般貨物のみ）を 1 か所で決める。
     *
     * <p>集約が書き込むときと、更新の差分を比べるときの両方で使う。既定を 2 か所に
     * 書くと、片方だけが「何も選ばなかった」を空のままにして、選んでいないだけの
     * 更新が「対応貨物種別が変わった」と差分に出る。</p>
     */
    public static java.util.List<String> resolveAcceptedNames(
            java.util.Set<CargoType> types) {
        java.util.Set<CargoType> resolved = types == null || types.isEmpty()
                ? java.util.Set.of(GENERAL)
                : new java.util.TreeSet<>(types);
        return resolved.stream().map(Enum::name).toList();
    }
}
