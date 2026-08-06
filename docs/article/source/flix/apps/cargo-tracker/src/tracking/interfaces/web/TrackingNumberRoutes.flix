///
/// 追跡番号の発行（US14。IT10）。
///
/// **専用の画面を作らない**。経路設計者は予約詳細で内容を確認してから発行する。
/// 発行のためだけの画面を挟むと、確認する材料（荷主・経路・確定状態）を
/// もう一度並べることになり、予約詳細と二重に持つことになる。
///
/// URL は `/bookings/{bookingId}/tracking-number` で **Booking の名前空間に相乗り**する。
/// 利用者から見て「その予約に対する操作」だからである。ハンドラが Tracking にあるのは
/// **追跡番号を採番するのが Tracking だから**であり、経路割り当て（`POST /bookings/{id}/route`）と
/// 同じ形である（ADR-0012）。
///
mod TrackingNumberRoutes {
    use SharedClock.Clock
    use SharedHttp.{Request, Response, pathParam}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedHttpFlash.Flash
    use TrackingBookingTrackingNumberAssignment.BookingTrackingNumberAssignment
    use TrackingRepo.TrackingRepo

    ///
    /// 追跡番号を発行して予約詳細へ戻す（PRG）。
    ///
    /// **成功も失敗も予約詳細へ戻す**。発行は予約詳細から始める操作であり、
    /// 結果を確認する場所も予約詳細である。通知（Flash）で結果を伝える。
    ///
    pub def issue(req: Request): Response \ TrackingRepo + Clock + BookingTrackingNumberAssignment + IO =
        let bookingId = pathParam(req, "bookingId") |> Option.getWithDefault("");
        let target = "/bookings/" + bookingId;
        match TrackingIssueTrackingNumber.issue(bookingId) {
            case TrackingIssueTrackingNumber.IssueResult.Issued(number) =>
                back(target, Flash.TrackingNumberIssued(number))
            case TrackingIssueTrackingNumber.IssueResult.BookingNotFound =>
                back(target, Flash.TrackingNumberRejected("NOT_FOUND"))
            case TrackingIssueTrackingNumber.IssueResult.BookingNotConfirmed =>
                back(target, Flash.TrackingNumberRejected("NOT_CONFIRMED"))
            case TrackingIssueTrackingNumber.IssueResult.AlreadyIssued =>
                back(target, Flash.TrackingNumberRejected("ALREADY_ISSUED"))
            case TrackingIssueTrackingNumber.IssueResult.NumberRejected =>
                // 採番したのは自分自身であり、ここへ来るのは実装の誤りである。
                // **黙って成功にしない**——番号が無いまま発行済みに見えるほうが害が大きい
                back(target, Flash.TrackingNumberRejected("NUMBERING_FAILED"))
        }

    /// 予約詳細へ戻し、結果を通知に載せる
    def back(target: String, f: Flash): Response \ IO =
        withHeader(redirect(target), "Set-Cookie",
            SharedHttpFlash.setCookie(f, SharedAppEnv.isSecureCookie()))
}
