package com.example.cargotracker.tracking.domain.model;

/**
 * 輸送状態（9 段階）。
 *
 * <p><strong>所有するのは Tracking Context である</strong>（ADR-005）。他の BC は
 * ACL ポート経由で、必要な粒度の自前型に変換して参照する。共有カーネルに置くと、
 * 状態を 1 つ増やすだけで全 BC の再ビルドとレビューを強制する。
 *
 * <p>表示ラベルとバッジの正典は {@code ui_design.md}「TransportStatus バッジ定義」で
 * ある。<strong>画面側で状態名を並べて分岐しない</strong>ため、列挙子自身が持つ。
 */
public enum TransportStatus {

    /** 未受取。追跡番号の発行直後。 */
    NOT_RECEIVED("未受取", "bg-secondary"),

    /** 受取済。出発港で貨物を受領した。 */
    RECEIVED("受取済", "bg-info text-dark"),

    /** 積み込み済。 */
    LOADED("積み込み済", "bg-primary"),

    /** 搭載中（出港済み）。手動更新で遷移する（US17 / IT8）。 */
    ONBOARD_CARRIER("搭載中", "bg-primary"),

    /** 荷降ろし済。 */
    UNLOADED("荷降ろし済", "bg-warning text-dark"),

    /** 引取待ち。 */
    AWAITING_CLAIM("引取待ち", "bg-warning text-dark"),

    /** 引取完了。 */
    CLAIMED("引取完了", "bg-success"),

    /** 例外。遅延・破損・紛失・税関保留（US19 / US20 / IT10）。 */
    EXCEPTION("例外", "bg-danger"),

    /** 不明。状態を確定できない場合。 */
    UNKNOWN("不明", "bg-secondary");

    private final String displayName;
    private final String badgeClass;

    TransportStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    /** 画面・メッセージに出す日本語名。**列挙子名を利用者に見せない**。 */
    public String displayName() {
        return displayName;
    }

    /** バッジの Bootstrap クラス。 */
    public String badgeClass() {
        return badgeClass;
    }

    /** 追跡番号を発行した直後の状態（US14 の受入基準「受領待ち」）。 */
    public static TransportStatus initial() {
        return NOT_RECEIVED;
    }
}
