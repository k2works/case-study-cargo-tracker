package com.example.cargotracker.handling.domain.model;

/**
 * 荷役種別と、その種別に応じて要る詳細のひと組。
 *
 * <p><strong>別々に持つと、成り立たない組み合わせを作れる。</strong>
 *
 * <ul>
 *   <li>積込なのに航海番号が無い（どの便に積んだか分からない）</li>
 *   <li>引取なのに荷受人確認が無い（証明の無い引き渡し）</li>
 *   <li><strong>受領なのに荷受人確認が付いている</strong>（どちらかの入力誤り）</li>
 *   <li>通関なのに航海番号が付いている（手続きに便は関係しない）</li>
 * </ul>
 *
 * <p>最後の 2 つは「必須でないものを許す」だけの実装では防げない。
 * <strong>要否は種別自身が知っており、ここがその判断を使う唯一の場所である。</strong>
 * 登録処理や画面に対応表を書き写すと、種別が増えたときに片方だけが更新される。
 *
 * <p>この形は DB の {@code chk_handling_claim_confirmation} と対応している。
 * <strong>同じ不変条件をドメインと DB の両方で守る</strong>（画面を経由しない
 * 登録経路でも成り立つ必要があるため）。
 *
 * <p>Checkstyle のパラメータ数上限に当たって作った箱ではない。
 * <strong>上限に当たったことを、概念を見直す合図として使った</strong>
 * （IT6 の {@code ProposedRoute.Path}・{@code CargoProgress}、
 * 本 IT の {@link HandledCargo} と同じ判断）。
 *
 * @param type              荷役種別
 * @param voyageNumber      航海番号（積込・荷降しでのみ持つ）
 * @param claimConfirmation 荷受人確認（引取でのみ持つ）
 */
public record HandlingDetails(
        HandlingType type,
        HandlingVoyageNumber voyageNumber,
        ClaimConfirmation claimConfirmation) {

    public HandlingDetails {
        if (type == null) {
            throw new IllegalArgumentException("荷役種別は必須です");
        }
        requireConsistent(type, voyageNumber != null, type.requiresVoyageNumber(), "航海番号");
        requireConsistent(type, claimConfirmation != null,
                type.requiresClaimConfirmation(), "荷受人確認");
    }

    /**
     * 「要るのに無い」と「要らないのに有る」の両方を弾く。
     *
     * <p>後者を許すと、受領に荷受人確認が付いた記録が残る。それは引き渡しの
     * 証明としても受領の記録としても読めない、<strong>意味の定まらない行</strong>である。
     */
    private static void requireConsistent(
            HandlingType type, boolean present, boolean required, String label) {
        if (required && !present) {
            throw new IllegalArgumentException(
                    "%s には%sが必要です".formatted(type.displayName(), label));
        }
        if (!required && present) {
            throw new IllegalArgumentException(
                    "%s に%sは指定できません".formatted(type.displayName(), label));
        }
    }

    /** 受領。 */
    public static HandlingDetails receive() {
        return new HandlingDetails(HandlingType.RECEIVE, null, null);
    }

    /** 通関。 */
    public static HandlingDetails customs() {
        return new HandlingDetails(HandlingType.CUSTOMS, null, null);
    }

    /** 積込。 */
    public static HandlingDetails load(HandlingVoyageNumber voyageNumber) {
        return new HandlingDetails(HandlingType.LOAD, voyageNumber, null);
    }

    /** 荷降し。 */
    public static HandlingDetails unload(HandlingVoyageNumber voyageNumber) {
        return new HandlingDetails(HandlingType.UNLOAD, voyageNumber, null);
    }

    /** 引取。 */
    public static HandlingDetails claim(ClaimConfirmation confirmation) {
        return new HandlingDetails(HandlingType.CLAIM, null, confirmation);
    }

    /**
     * 種別と、その種別に応じた詳細から組み立てる。
     *
     * <p>画面から届く値は種別が実行時に決まるため、<strong>要らない詳細が
     * 混ざって届く</strong>（引取を選ぶ前に航海番号を打っていた等）。
     * <strong>種別が要らないと言う詳細は捨てる。</strong> 捨てずに弾くと、
     * 種別を選び直すたびに入力し直させることになる。
     */
    public static HandlingDetails of(
            HandlingType type,
            HandlingVoyageNumber voyageNumber,
            ClaimConfirmation claimConfirmation) {
        if (type == null) {
            throw new IllegalArgumentException("荷役種別は必須です");
        }
        return new HandlingDetails(
                type,
                type.requiresVoyageNumber() ? voyageNumber : null,
                type.requiresClaimConfirmation() ? claimConfirmation : null);
    }
}
