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

/// 荷主への通知ポート（US14: 追跡番号通知 / US15/US17: 状態変更通知 / US19/US20: 例外通知）。
type TrackingNotifier =
    { Notify: TrackingNumber -> string -> Async<Result<unit, DomainError>> }

/// 管理職へのエスカレーション通知ポート（US20: 紛失時の緊急通知）。
type EscalationNotifier =
    { Escalate: TrackingNumber -> ExceptionType -> Async<Result<unit, DomainError>> }

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

            // 公開追跡 URL（access_token）を通知に同梱し、荷主が認証なしで照会・URL 共有できる導線を提供する（レビュー高#6）。
            let message =
                sprintf
                    "予約 %s の追跡番号 %s を発行しました。認証ありは /tracking、認証なしは /public/tracking/%s で照会できます。"
                    (TrackingBookingId.value bookingId)
                    (TrackingNumber.value activity.TrackingNumber)
                    token

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

module ManageException =

    open FsToolkit.ErrorHandling

    /// 発行済みイベントに ExceptionEscalated が含まれるか（Lost 時）。
    let private hasEscalation (events: TrackingEvent list) : bool =
        events
        |> List.exists (function
            | ExceptionEscalated _ -> true
            | _ -> false)

    /// 例外を登録する（US19 遅延 / US20 破損・紛失）。登録後、荷主へ通知し、
    /// Lost（エスカレーション）時は管理職へ緊急通知する。状態は InException を導出する。
    let register
        (repo: TrackingRepository)
        (notifier: TrackingNotifier)
        (escalation: EscalationNotifier)
        (trackingNumber: TrackingNumber)
        (exType: ExceptionType)
        (location: Location)
        (occurredAt: System.DateTimeOffset)
        (description: string)
        : Async<Result<TrackingActivity, DomainError>> =
        asyncResult {
            let! found = repo.FindByTrackingNumber trackingNumber

            let! activity =
                match found with
                | Some a -> Ok a
                | None -> Error(NotFound("TrackingActivity", TrackingNumber.value trackingNumber))

            let! updated, events =
                TrackingActivity.execute activity (RegisterException(exType, location, occurredAt, description))

            do! repo.Update updated

            let message =
                sprintf
                    "追跡番号 %s で例外（%s）が発生しました: %s"
                    (TrackingNumber.value trackingNumber)
                    (ExceptionType.toString exType)
                    description

            do! notifier.Notify trackingNumber message

            if hasEscalation events then
                do! escalation.Escalate trackingNumber exType

            return updated
        }

    /// 例外を解決する（対応報告）。解決後、状態は例外発生前へ導出復帰し、荷主へ対応報告を通知する。
    let resolve
        (repo: TrackingRepository)
        (notifier: TrackingNotifier)
        (trackingNumber: TrackingNumber)
        (index: int)
        (resolvedAt: System.DateTimeOffset)
        (resolutionNote: string)
        : Async<Result<TrackingActivity, DomainError>> =
        asyncResult {
            let! found = repo.FindByTrackingNumber trackingNumber

            let! activity =
                match found with
                | Some a -> Ok a
                | None -> Error(NotFound("TrackingActivity", TrackingNumber.value trackingNumber))

            let! updated, _events =
                TrackingActivity.execute activity (ResolveException(index, resolvedAt, resolutionNote))

            do! repo.Update updated

            let message =
                sprintf "追跡番号 %s の例外に対応しました: %s" (TrackingNumber.value trackingNumber) resolutionNote

            do! notifier.Notify trackingNumber message
            return updated
        }
