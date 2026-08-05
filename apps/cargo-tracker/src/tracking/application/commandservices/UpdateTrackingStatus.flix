///
/// 貨物状態の手動更新（US17。IT11）。
///
/// 追跡管理者が、荷役では捕捉できない状態変化（出港・入港等）を記録する。
///
/// **荷役の反映（`ApplyHandling`）と別のサービスにする**。同じ入口にすると
/// 「荷役は遷移を見ない・手動は見る」という**違う規則が 1 つの関数に同居**する。
/// 規則が違うものは入口を分ける。
///
mod TrackingUpdateStatus {
    use SharedTransportStatus.TransportStatus
    use TrackingModel.TrackingError
    use TrackingRepo.TrackingRepo

    pub enum UpdateResult with Eq, ToString {
        case Updated
        case NotFound
        case Rejected(TrackingError)
    }

    ///
    /// 状態を手で更新する。
    ///
    /// **`findForUpdate` で行を押さえる**。荷役の登録と手動更新は
    /// どちらも同じ行を書く。押さえないと後着順で決まり、
    /// 「巻き戻しを拒む」判定が読んだ時点の古い状態に対して行われる。
    ///
    /// `latestEventTime` は時刻の逆行を拒むために要る。**集約には引数で渡す**
    /// ——集約が履歴を引きに行く形にすると、効果を要求することになる。
    ///
    /// **直近の時刻はロックを取った後に読む**（IT11 レビュー）。当初は
    /// 画面側が読み取りポート（`ReadDb`）から先に取って渡していたが、それでは
    /// `findForUpdate` の**外**で読むことになり、**状態の巻き戻しは守られるのに
    /// 時刻の逆行だけ守られない**という非対称になっていた。
    /// 不変条件の材料は、その不変条件を守る場所で揃える。
    ///
    pub def update(trackingNumber: String, status: TransportStatus,
                   location: String, occurredAt: String): UpdateResult \ TrackingRepo =
        match TrackingRepo.findForUpdate(trackingNumber) {
            case None    => UpdateResult.NotFound
            case Some(a) =>
                match TrackingModel.updateManually(a, status, location, occurredAt,
                                                   TrackingRepo.latestEventTimeOf(trackingNumber)) {
                    case Err(e)      => UpdateResult.Rejected(e)
                    case Ok(updated) => {
                        TrackingRepo.saveHandling(updated);
                        UpdateResult.Updated
                    }
                }
        }
}
