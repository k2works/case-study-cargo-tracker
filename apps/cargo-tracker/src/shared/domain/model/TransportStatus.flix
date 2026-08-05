///
/// 貨物の輸送状態（共有カーネル。IT10・US14/US15）。
///
/// # なぜ共有カーネルか
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md)の用語集が
/// 「Shared Domain」と定めている。Tracking が `TrackingActivity` に持ち、
/// Handling が荷役の結果として遷移させ、公開追跡（US03）が表示する。
///
/// **3 つの BC が実際に参照する**——`RoutingStatus` が IT9 時点で Booking しか
/// 使っていなかったのとは違い、共有の実態が最初からある。
///
/// # `BookingStatus`・`RoutingStatus` とは別の軸である
///
/// 予約が「予約確定」であること・経路が「決まっている」こと・貨物が「積み込まれた」ことは
/// 独立に動く。畳むと、**荷役が起きていないのに輸送中に見える**か、
/// **積み込んだのに予約が動かない**かのどちらかになる。
///
mod SharedTransportStatus {

    ///
    /// 輸送状態。
    ///
    /// 本 IT で遷移させるのは `NotReceived`（US14 の発行時）と、
    /// 荷役による `Received` / `Loaded` / `Unloaded`（US15）である。
    ///
    /// `OnboardCarrier`（出港）・`AwaitingClaim`（引取待ち）・`Claimed`（引取完了）は
    /// **値だけ定義する**。出港の記録は本 IT の対象外、引取は US16（IT11）である。
    /// 値を先に置くのは、パターンマッチの網羅性検査を効かせるためである。
    ///
    /// `Exception` は US19/US20（例外処理）、`Unknown` は**読めなかった行**ではなく
    /// 「業務として状態が不明」を表す（読めない行は `fromPersisted` が `None` を返す）。
    ///
    pub enum TransportStatus with Eq, ToString {
        case NotReceived
        case Received
        case Loaded
        case OnboardCarrier
        case Unloaded
        case AwaitingClaim
        case Claimed
        case Exception
        case Unknown
    }

    ///
    /// 永続化値から復元する。
    ///
    /// **未知の値は `None`**。既定値へ倒すと、輸送中の貨物が未受領に見える——
    /// 荷主が「まだ集荷されていない」と誤解し、営業に問い合わせが来る。
    /// 行を落とすほうが害が小さい（読み出し側が「読めなかった」と言える）。
    ///
    pub def fromPersisted(value: String): Option[TransportStatus] = match value {
        case "NOT_RECEIVED"    => Some(TransportStatus.NotReceived)
        case "RECEIVED"        => Some(TransportStatus.Received)
        case "LOADED"          => Some(TransportStatus.Loaded)
        case "ONBOARD_CARRIER" => Some(TransportStatus.OnboardCarrier)
        case "UNLOADED"        => Some(TransportStatus.Unloaded)
        case "AWAITING_CLAIM"  => Some(TransportStatus.AwaitingClaim)
        case "CLAIMED"         => Some(TransportStatus.Claimed)
        case "EXCEPTION"       => Some(TransportStatus.Exception)
        case "UNKNOWN"         => Some(TransportStatus.Unknown)
        case _                 => None
    }

    pub def toPersisted(s: TransportStatus): String = match s {
        case TransportStatus.NotReceived    => "NOT_RECEIVED"
        case TransportStatus.Received       => "RECEIVED"
        case TransportStatus.Loaded         => "LOADED"
        case TransportStatus.OnboardCarrier => "ONBOARD_CARRIER"
        case TransportStatus.Unloaded       => "UNLOADED"
        case TransportStatus.AwaitingClaim  => "AWAITING_CLAIM"
        case TransportStatus.Claimed        => "CLAIMED"
        case TransportStatus.Exception      => "EXCEPTION"
        case TransportStatus.Unknown        => "UNKNOWN"
    }

    //
    // **表示名はここに置かない**（IT11・M14）。IT10 は `label` を持っていたが、
    // 画面は `SharedHtmlComponents` の対応表を使っており、
    // **ドメイン側は一度も呼ばれていなかった**——片方だけ直しても誰も気付かない
    // （`HandlingModel.validityMessage` と同じ形。M15）。
    //
    // 画面の言葉は表示の関心である。正典は
    // [UI 設計](../../../../../../docs/design/ui_design.md)の
    // 「TransportStatus バッジ定義」で、実装は `SharedHtmlComponents` が持つ。
    //
}
