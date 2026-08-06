///
/// 追跡番号の発行（US14。IT10）。
///
/// 経路設計者が確定済みの予約に対して実行する。
///
/// # 手順が 2 つの集約にまたがる
///
/// 1. `TrackingActivity` を作る（追跡の台帳。輸送状態は「受領待ち」で始まる）
/// 2. Booking へ番号を記録する（ACL ポート経由。集約が状態を進める）
///
/// **1 トランザクションで 2 つの集約を更新する**——
/// [ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)
/// が引き受けたリスクである。片方だけ成功した状態は業務上ありえない
/// （追跡番号が発行されたのに予約が知らない＝営業が番号を答えられない）。
///
/// **Booking を先に見る**。予約が確定していなければ台帳を作る前に止まる——
/// 逆順にすると、拒まれたときに使われない `tracking_activity` の行が残る。
///
mod TrackingIssueTrackingNumber {
    use SharedClock.Clock
    use TrackingBookingTrackingNumberAssignment.{BookingTrackingNumberAssignment, AssignmentResult}
    use TrackingModel.TrackingActivity
    use TrackingRepo.TrackingRepo

    pub enum IssueResult with Eq, ToString {
        case Issued(String)
        /// 予約が見つからない
        case BookingNotFound
        /// 予約が確定していない
        case BookingNotConfirmed
        /// 既に発行済み
        case AlreadyIssued
        /// 採番した番号が形式として妥当でない（起こらないはずの経路）
        case NumberRejected
    }

    ///
    /// 追跡番号を発行する。
    ///
    /// 採番は `TRK-YYYYMMDD-NNNN`。**日付を含むため同一日内の連番が競合点**になる。
    /// 一意性を保証するのは DB の UNIQUE 制約であり、本サービスではない
    /// （`TrackingRepo.nextSequenceFor` のドキュメントを参照）。
    ///
    pub def issue(bookingId: String): IssueResult \ TrackingRepo + Clock + BookingTrackingNumberAssignment =
        // 既に台帳がある＝発行済み。Booking を呼ぶ前に止める
        if (not Option.isEmpty(TrackingRepo.findByBookingId(bookingId)))
            IssueResult.AlreadyIssued
        else
            let today = Clock.today();
            let number = TrackingModel.formatNumber(today, TrackingRepo.nextSequenceFor(today));
            match TrackingModel.issue(number, bookingId) {
                case Err(_)       => IssueResult.NumberRejected
                case Ok(activity) =>
                    // **Booking を先に更新する**。拒まれたら台帳を作らない
                    match BookingTrackingNumberAssignment.assign(bookingId, number) {
                        case AssignmentResult.BookingNotFound     => IssueResult.BookingNotFound
                        case AssignmentResult.BookingNotConfirmed => IssueResult.BookingNotConfirmed
                        case AssignmentResult.AlreadyIssued       => IssueResult.AlreadyIssued
                        case AssignmentResult.Assigned            => {
                            TrackingRepo.save(activity);
                            IssueResult.Issued(number)
                        }
                    }
            }
}
