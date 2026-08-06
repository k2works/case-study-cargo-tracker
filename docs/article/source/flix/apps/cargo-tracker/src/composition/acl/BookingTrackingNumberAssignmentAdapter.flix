///
/// 追跡番号の記録の翻訳（Tracking → Booking の ACL アダプタ。IT10・US14）。
///
/// # なぜここに置くか
///
/// **BC 間の翻訳の唯一の置き場所**である（`arch-lint` 規約 11。
/// [ADR-0012](../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)）。
/// `tracking/` 配下に置くと規約 4 に、`*Wiring.flix` に置くと規約 11 に違反する。
///
/// # 本アダプタは判断を持たない
///
/// Tracking の呼び出しを Booking のアプリケーションサービスへ写すだけである。
/// 「確定済みか」「二重発行でないか」は `Cargo.issueTrackingNumber`（集約）が決める——
/// **ここで検証を書くと、集約の不変条件が 2 箇所になる**。
///
mod BookingTrackingNumberAssignmentAdapter {
    use TrackingBookingTrackingNumberAssignment.{BookingTrackingNumberAssignment, AssignmentResult}
    use CargoRepo.CargoRepo

    /// 本アダプタが要求する効果（配線ファイルに Booking の効果名を書かせない）
    ///
    /// **向きを宣言する**（`arch-lint` 規約 13・ADR-0014 決定 4）。
    ///
    /// 単一 BC へ向かう書き込みが増えると、その BC の集約がハブ化している。
    /// ファイル名から推測させない——推測は命名を変えるだけで回避できる。
    ///
    pub def aclTarget(): String = "booking:write"

    pub type alias Requires = CargoRepo

    pub def withBookingTrackingNumberAssignment(
            f: Unit -> a \ ef + BookingTrackingNumberAssignment):
            a \ (ef - BookingTrackingNumberAssignment) + Requires =
        run {
            f()
        } with handler BookingTrackingNumberAssignment {
            def assign(bookingId, trackingNumber, k) =
                k(assignInBooking(bookingId, trackingNumber))
        }

    def assignInBooking(bookingId: String, trackingNumber: String):
            AssignmentResult \ CargoRepo =
        match BookingIssueTrackingNumber.issue(bookingId, trackingNumber) {
            case BookingIssueTrackingNumber.IssueResult.Issued     => AssignmentResult.Assigned
            case BookingIssueTrackingNumber.IssueResult.NotFound   => AssignmentResult.BookingNotFound
            case BookingIssueTrackingNumber.IssueResult.Rejected(e) =>
                // **理由を区別して返す**。「確定していない」は営業に確定を頼む話、
                // 「発行済み」は既にある番号を使う話であり、取れる行動が違う
                match e {
                    case BookingModel.BookingError.TrackingNumberIsAlreadyIssued =>
                        AssignmentResult.AlreadyIssued
                    case _ => AssignmentResult.BookingNotConfirmed
                }
        }
}
