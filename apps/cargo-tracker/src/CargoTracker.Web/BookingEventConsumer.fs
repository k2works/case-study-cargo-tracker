/// Booking のドメインイベントを消費する合成層（retro-4 Try#1・BC 間イベント駆動）。
///
/// `BookingConfirmed` を契機に Tracking の追跡番号を自動発行する（US14）。
/// 各 BC は他 BC を直接参照せず、結線はこの合成層に閉じる。dispatch はベストエフォート
/// （applyCommand が post-commit で握るため、ここでの失敗は確定済みの予約を巻き戻さない）。
module CargoTracker.Web.BookingEventConsumer

open System.Data
open Donald
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain
open CargoTracker.Booking.Application
open CargoTracker.Tracking.Application

/// notification_log へ通知を記録する TrackingNotifier（US14 荷主通知）。
/// booking_id には予約 ID を、recipient には荷主識別子（現状は予約 ID を代理利用）を記録する。
/// 追跡番号ではなく予約 ID を通知先キーにする（レビュー中#3・荷主連絡先モデル化は将来 IT）。
let private trackingNotifier (conn: IDbConnection) (clock: Clock) (bookingId: string) : TrackingNotifier =
    { Notify =
        fun _trackingNumber message ->
            async {
                try
                    let now = (clock ()).UtcDateTime.ToString("o")

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO notification_log (booking_id, recipient, message, notified_at, created_at)
                        VALUES (@booking_id, @recipient, @message, @now, @now)
                        """
                    |> Db.setParams
                        [ "booking_id", SqlType.String bookingId
                          "recipient", SqlType.String bookingId
                          "message", SqlType.String message
                          "now", SqlType.String now ]
                    |> Db.exec

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("TrackingNotifier", ex.Message))
            } }

/// 追跡活動が未発行の予約に対してのみ追跡番号を発行する（冪等・二重発行防止）。
let private alreadyIssued (conn: IDbConnection) (bookingId: string) : bool =
    conn
    |> Db.newCommand "SELECT COUNT(*) AS cnt FROM tracking_activity WHERE booking_id = @bid"
    |> Db.setParams [ "bid", SqlType.String bookingId ]
    |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")
    |> Option.defaultValue 0
    |> (fun n -> n > 0)

/// Booking イベントディスパッチャの実消費実装（合成層）。
/// BookingConfirmed → Tracking 追跡番号発行（US14）。それ以外のイベントは現状消費先なし（ログのみ）。
let create (conn: IDbConnection) (clock: Clock) (newId: IdGenerator) : BookingEventDispatcher =
    { Dispatch =
        fun (event: BookingEvent) ->
            async {
                // dispatch はベストエフォート。alreadyIssued の COUNT を含め DB 例外を握り、
                // 確定済み予約（applyCommand が post-commit）を巻き戻さない。
                try
                    match event with
                    | BookingConfirmed bookingId ->
                        let bid = BookingId.value bookingId

                        if not (alreadyIssued conn bid) then
                            let repo = CargoTracker.Tracking.Infrastructure.TrackingRepository.create conn clock
                            let notifier = trackingNotifier conn clock bid
                            let trackingBookingId = CargoTracker.Tracking.Domain.TrackingBookingId.ofString bid

                            let! result = IssueTracking.issue repo notifier newId trackingBookingId

                            match result with
                            | Ok _ -> ()
                            | Error e -> printfn "[BookingEventConsumer] 追跡番号発行に失敗: %A" e
                    | other -> printfn "[BookingEventConsumer] 未消費イベント: %A" other
                with ex ->
                    printfn "[BookingEventConsumer] dispatch 例外を握り潰し（予約は確定済み）: %s" ex.Message
            } }
