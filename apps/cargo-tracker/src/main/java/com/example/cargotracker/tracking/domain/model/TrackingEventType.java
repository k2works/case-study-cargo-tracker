package com.example.cargotracker.tracking.domain.model;

import java.util.Optional;

/**
 * 追跡イベントの種別。
 *
 * <p>荷役の種別（{@code HandlingType}）と値は同じだが、<strong>「荷役作業として
 * 何をしたか」と「追跡の上で何が起きたか」は別の事実である</strong>。荷役は
 * Handling モジュールが持ち、追跡は本列挙型が持つ（IT5 の {@code CargoRoutingStatus}
 * と同じ整理）。
 *
 * <p>どの種別がどの輸送状態に進めるかは<strong>本列挙型が知っている</strong>。
 * 画面や登録処理に対応表を書き写すと、種別が増えたときに片方だけが更新される。
 */
public enum TrackingEventType {

    /** 受領。出発港での貨物受領。 */
    RECEIVE("受領", TransportStatus.RECEIVED),

    /** 積込。航海への積み込み。 */
    LOAD("積込", TransportStatus.LOADED),

    /** 荷降し。航海からの荷降ろし。 */
    UNLOAD("荷降し", TransportStatus.UNLOADED),

    /**
     * 通関。
     *
     * <p><strong>輸送状態は動かない。</strong> 通関は貨物の位置を変えない手続きであり、
     * 通関状態（{@code CustomsStatus}）として別に管理する（US29 / IT11）。
     */
    CUSTOMS("通関", null),

    /** 引取。目的港での貨物引取（US16 / IT7）。 */
    CLAIM("引取", TransportStatus.CLAIMED);

    private final String displayName;
    private final TransportStatus resultingStatus;

    TrackingEventType(String displayName, TransportStatus resultingStatus) {
        this.displayName = displayName;
        this.resultingStatus = resultingStatus;
    }

    /** 画面に出す日本語名（正典は {@code ui_design.md}「HandlingType 表示ラベル定義」）。 */
    public String displayName() {
        return displayName;
    }

    /** このイベントで進む輸送状態。動かない種別では空を返す。 */
    public Optional<TransportStatus> resultingStatus() {
        return Optional.ofNullable(resultingStatus);
    }
}
