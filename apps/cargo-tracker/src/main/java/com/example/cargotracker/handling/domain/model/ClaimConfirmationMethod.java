package com.example.cargotracker.handling.domain.model;

/**
 * 荷受人確認の方法（US16）。
 *
 * <p><strong>署名（SIGNATURE）は列挙子として置かない。</strong> 押しても何も
 * 起きない選択肢を作らないためである（IT4〜IT6 と同じ扱い）。署名の取得は
 * キャンバス入力と画像の永続化を伴い、IT7 の範囲に収まらない。
 *
 * <p>ただし {@link ClaimConfirmation} は「方法」を持つ形にしてある。
 * <strong>署名を足すときにモデルを壊さずに済む</strong>ようにするためであり、
 * 列挙子を 1 つ増やすだけで足りる。
 */
public enum ClaimConfirmationMethod {

    /** 確認コード。荷受人に事前送付した 6 桁を照合する。 */
    CONFIRMATION_CODE("確認コード");

    private final String displayName;

    ClaimConfirmationMethod(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す日本語名。**列挙子名を利用者に見せない**。 */
    public String displayName() {
        return displayName;
    }
}
