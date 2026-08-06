///
/// 経路の決定状態（共有カーネル。IT9・US11）。
///
/// # なぜ共有カーネルか
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md)の用語集が
/// 「Shared Domain」と定めている。Booking が `Cargo` に持ち、
/// Tracking が輸送状態と併せて参照する（IT10 以降）。
///
/// **`BookingStatus` とは別の軸である**。予約が「経路設計中」であることと、
/// 経路が「決まっている」ことは独立に動く——経路を紐付けても予約は
/// 経路設計中のままであり、確定するのは営業が荷主の承認を得たとき（US13）である。
///
/// 2 つの軸を 1 つの列挙に畳むと、**荷主未承認のまま「予約確定」になる**か、
/// **経路が決まったのに状態が変わらない**かのどちらかになる。
///
mod SharedRoutingStatus {

    ///
    /// 経路の決定状態。
    ///
    /// `Misrouted` は本 IT では**遷移させない**。旅程が予約の要件を
    /// 満たさなくなった状態（航海の変更で端点がずれた等）であり、
    /// 検出には航海の更新を監視する仕組みが要る（IT10 以降）。
    /// 値だけ定義するのは、パターンマッチの網羅性検査を効かせるためである。
    ///
    pub enum RoutingStatus with Eq, ToString {
        case NotRouted
        case Routed
        case Misrouted
    }

    /// 未知の値は `None`。既定値へ倒すと、経路が決まっていない予約が決定済みに見える
    pub def fromPersisted(value: String): Option[RoutingStatus] = match value {
        case "NOT_ROUTED" => Some(RoutingStatus.NotRouted)
        case "ROUTED"     => Some(RoutingStatus.Routed)
        case "MISROUTED"  => Some(RoutingStatus.Misrouted)
        case _            => None
    }

    pub def toPersisted(s: RoutingStatus): String = match s {
        case RoutingStatus.NotRouted => "NOT_ROUTED"
        case RoutingStatus.Routed    => "ROUTED"
        case RoutingStatus.Misrouted => "MISROUTED"
    }
}
