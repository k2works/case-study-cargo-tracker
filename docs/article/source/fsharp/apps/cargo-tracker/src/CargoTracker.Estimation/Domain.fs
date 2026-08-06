namespace CargoTracker.Estimation.Domain

open System
open CargoTracker.Shared.Domain

// Estimation コンテキストのドメイン層（US01: 輸送見積を作成する）。

/// 見積の一意識別子（Guid ベース）。
type EstimateId = private EstimateId of Guid

module EstimateId =

    /// Guid.NewGuid() を直接呼ばず、IdGenerator ポート（unit -> Guid）を引数で受けて純粋性を保つ（ADR-0006）。
    let generate (newId: IdGenerator) : EstimateId = EstimateId(newId ())

    /// 既存の Guid から復元する（永続化層用）。
    let ofGuid (value: Guid) : EstimateId = EstimateId value

    let value (EstimateId v) = v

/// 正の値かつ 30,000 kg（コンテナ最大積載相当）以下のみを許容する重量。
type WeightKg = private WeightKg of decimal

module WeightKg =

    /// コンテナ最大積載相当の重量上限（kg）。
    let maxWeightKg = 30_000m

    let create (value: decimal) : Result<WeightKg, DomainError> =
        if value <= 0m then
            Error(ValidationError("WeightKg", "重量は正の値でなければなりません。"))
        elif value > maxWeightKg then
            Error(ValidationError("WeightKg", "重量は 30,000 kg（コンテナ最大積載相当）以下でなければなりません。"))
        else
            Ok(WeightKg value)

    let value (WeightKg v) = v

/// 見積の貨物種別（Estimation はフラグのみ保持。詳細申告は Booking の責務）。
type CargoType =
    | General
    | Hazardous
    | Refrigerated

/// 見積に紐づく輸送ルート候補。
type RouteCandidate =
    { VoyageNumber: string
      TransitPort: string
      TransitDays: int
      EstimatedCost: decimal }

module RouteCandidate =

    let create
        (voyageNumber: string)
        (transitPort: string)
        (transitDays: int)
        (estimatedCost: decimal)
        : Result<RouteCandidate, DomainError> =
        if String.IsNullOrWhiteSpace voyageNumber then
            Error(ValidationError("VoyageNumber", "航海番号は空にできません。"))
        elif transitDays <= 0 then
            Error(ValidationError("TransitDays", "輸送日数は正の値でなければなりません。"))
        elif estimatedCost <= 0m then
            Error(ValidationError("EstimatedCost", "見積コストは正の値でなければなりません。"))
        else
            Ok
                { VoyageNumber = voyageNumber
                  TransitPort = transitPort
                  TransitDays = transitDays
                  EstimatedCost = estimatedCost }

/// 見積状態。
type EstimateStatus =
    | Created
    | Expired

/// 見積作成イベント（US01）。BC 固有イベントはローカル record とする（ADR-0002 の Shared 循環回避方針）。
type EstimateCreated = { EstimateId: EstimateId }

/// 集約ルート。輸送見積とルート候補を管理する。
type Estimate =
    { EstimateId: EstimateId
      Origin: Location
      Destination: Location
      ArrivalDeadline: DateOnly
      CargoType: CargoType
      WeightKg: WeightKg
      Candidates: RouteCandidate list
      Status: EstimateStatus }

module Estimate =

    open FsToolkit.ErrorHandling

    /// 見積を新規作成する。newId は IdGenerator ポート（ADR-0006）でアプリケーション層から注入する。
    let create
        (newId: IdGenerator)
        (origin: Location)
        (destination: Location)
        (arrivalDeadline: DateOnly)
        (cargoType: CargoType)
        (weightKg: WeightKg)
        : Result<Estimate * EstimateCreated, DomainError> =
        result {
            do!
                if Location.sameAs origin destination then
                    Error(BusinessRuleViolation("SameOriginDestination", "同一地点への見積は作成できません。"))
                else
                    Ok()

            let estimateId = EstimateId.generate newId

            let estimate =
                { EstimateId = estimateId
                  Origin = origin
                  Destination = destination
                  ArrivalDeadline = arrivalDeadline
                  CargoType = cargoType
                  WeightKg = weightKg
                  Candidates = []
                  Status = Created }

            return estimate, { EstimateId = estimateId }
        }

    /// ルート候補の一括入替（イミュータブル更新）。期限切れ見積には適用できない。
    let replaceCandidates (candidates: RouteCandidate list) (estimate: Estimate) : Result<Estimate, DomainError> =
        match estimate.Status with
        | Expired -> Error(InvalidStateTransition("Expired", "ReplaceCandidates"))
        | Created ->
            Ok
                { estimate with
                    Candidates = candidates }
