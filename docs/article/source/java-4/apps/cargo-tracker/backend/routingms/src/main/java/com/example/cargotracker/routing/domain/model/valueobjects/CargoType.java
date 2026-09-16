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
    /** 冷凍・冷蔵貨物。<b>Booking では {@code REFRIGERATED}</b>（{@link #fromContractName}）。 */
    REEFER;

    /**
     * 契約で運ばれる貨物種別の名前を、自 BC の列挙型に組み直す。
     *
     * <p><b>契約の語彙は Booking のもの</b>（{@code GENERAL} / {@code HAZARDOUS} /
     * {@code REFRIGERATED}）である。追跡の契約イベントも同じ語彙で billingms の
     * 料率表まで届いており、出典が 1 つでなければ片方だけが直る。冷凍だけ
     * Routing で呼び名が違う（{@code REEFER}）ので、<b>境界で翻訳する</b>——
     * 翻訳しないと、冷凍の予約は経路候補を 1 件も見られない（422 で断られる）。</p>
     *
     * @throws IllegalArgumentException 知らない名前のとき（<b>素通りさせない</b>）
     */
    public static CargoType fromContractName(String name) {
        if ("REFRIGERATED".equals(name)) {
            return REEFER;
        }
        return valueOf(name);
    }

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
