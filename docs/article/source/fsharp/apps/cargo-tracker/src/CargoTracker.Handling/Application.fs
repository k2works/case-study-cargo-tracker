namespace CargoTracker.Handling.Application

open CargoTracker.Shared.Domain
open CargoTracker.Handling.Domain

// Handling コンテキストのアプリケーション層（US15: 荷役記録 / US16: 引取記録）。
// 貨物情報（CargoSnapshot）は ACL ポート経由で取得し、Handling は Booking を直接参照しない（BC 分離）。

/// 荷役作業リポジトリの出力ポート。
type HandlingRepository =
    { Save: HandlingActivity -> Async<Result<unit, DomainError>> }

/// 貨物スナップショット取得の ACL ポート（追跡番号→貨物情報）。合成層で Booking から解決する。
type CargoSnapshotProvider =
    { FindByTrackingNumber: string -> Async<Result<CargoSnapshot option, DomainError>> }

module RegisterHandling =

    open FsToolkit.ErrorHandling

    /// 荷役作業を登録する（US15/US16）。
    /// 追跡番号で貨物を特定（ACL）→ 妥当性検証込みで登録 → 永続化。検証結果（Warning/Misrouted）を返す。
    let register
        (repo: HandlingRepository)
        (snapshotProvider: CargoSnapshotProvider)
        (trackingNumber: string)
        (handlingType: HandlingType)
        (location: Location)
        (completionTime: System.DateTimeOffset)
        (consigneeConfirmation: string option)
        : Async<Result<HandlingActivity * ValidationOutcome * HandlingEvent list, DomainError>> =
        asyncResult {
            let! found = snapshotProvider.FindByTrackingNumber trackingNumber

            let! snapshot =
                match found with
                | Some s -> Ok s
                | None -> Error(NotFound("TrackingNumber", trackingNumber))

            let! activity, outcome, events =
                HandlingActivity.register snapshot handlingType location completionTime consigneeConfirmation

            do! repo.Save activity
            return activity, outcome, events
        }
