namespace CargoTracker.Tracking.Application

open CargoTracker.Shared.Domain
open CargoTracker.Tracking.Domain

// Tracking コンテキストのアプリケーション層（US14: 発行 / US15・US17: イベント記録 / US18: 照会）。
// 永続化は Port（関数レコード）で抽象化し、ドメインは純粋に保つ。

/// 追跡活動リポジトリの出力ポート。access_token は公開照会（US18）用にアプリ層で採番して渡す。
type TrackingRepository =
    { Save: TrackingActivity -> string -> Async<Result<unit, DomainError>>
      FindByTrackingNumber: TrackingNumber -> Async<Result<TrackingActivity option, DomainError>>
      Update: TrackingActivity -> Async<Result<unit, DomainError>> }

/// 荷主への通知ポート（US14: 追跡番号通知 / US15/US17: 状態変更通知）。
type TrackingNotifier =
    { Notify: TrackingNumber -> string -> Async<Result<unit, DomainError>> }

module IssueTracking =

    open FsToolkit.ErrorHandling

    /// 公開照会用の推測困難トークンを採番する（Guid 2 本＝64 桁相当）。
    let private generateToken (newId: IdGenerator) : string =
        let a = (newId ()).ToString("N")
        let b = (newId ()).ToString("N")
        (a + b).ToUpperInvariant()

    /// 予約確定を契機に追跡番号を発行する（US14）。発行後、荷主へ通知する。
    let issue
        (repo: TrackingRepository)
        (notifier: TrackingNotifier)
        (newId: IdGenerator)
        (bookingId: TrackingBookingId)
        : Async<Result<TrackingActivity, DomainError>> =
        asyncResult {
            let activity, _events = TrackingActivity.issue newId bookingId
            let token = generateToken newId
            do! repo.Save activity token

            let message =
                sprintf
                    "予約 %s の追跡番号 %s を発行しました。/tracking で照会できます。"
                    (TrackingBookingId.value bookingId)
                    (TrackingNumber.value activity.TrackingNumber)

            do! notifier.Notify activity.TrackingNumber message
            return activity
        }

module RecordTracking =

    open FsToolkit.ErrorHandling

    /// 追跡イベントを記録し状態を進める（US15 荷役反映 / US17 手動更新）。記録後、荷主へ通知する。
    let record
        (repo: TrackingRepository)
        (notifier: TrackingNotifier)
        (trackingNumber: TrackingNumber)
        (event: TrackingActivityEvent)
        : Async<Result<TrackingActivity, DomainError>> =
        asyncResult {
            let! found = repo.FindByTrackingNumber trackingNumber

            let! activity =
                match found with
                | Some a -> Ok a
                | None -> Error(NotFound("TrackingActivity", TrackingNumber.value trackingNumber))

            let! updated, _events = TrackingActivity.execute activity (RecordEvent event)
            do! repo.Update updated

            let message = sprintf "追跡番号 %s の状態が更新されました。" (TrackingNumber.value trackingNumber)

            do! notifier.Notify trackingNumber message
            return updated
        }
