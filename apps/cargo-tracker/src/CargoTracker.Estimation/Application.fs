namespace CargoTracker.Estimation.Application

open System
open CargoTracker.Shared.Domain
open CargoTracker.Estimation.Domain

// Estimation コンテキストのアプリケーション層（US01: 輸送見積を作成する）。
// ルート候補算出は外部経路サービスの ACL ポートに委譲する（IT1 はスタブ、IT3 で実装差し替え）。

/// 見積リポジトリの出力ポート（関数レコード）。
type EstimateRepository =
    { Save: Estimate -> Async<Result<unit, DomainError>> }

/// 外部経路システムへの ACL ポート。出発地・目的地・期限からルート候補を取得する。
/// IT1 は WireMock.Net で契約を固定したスタブ、IT3 で実サービスに差し替える。
type RouteQuery =
    { Origin: Location
      Destination: Location
      ArrivalDeadline: DateOnly
      CargoType: CargoType }

type ExternalRoutingServicePort =
    { FetchCandidateRoutes: RouteQuery -> Async<Result<RouteCandidate list, DomainError>> }

/// 見積作成コマンド（UI からの DTO。UN/LOCODE と重量は文字列/数値で受ける）。
type CreateEstimateCommand =
    { OriginUnlocode: string
      DestinationUnlocode: string
      ArrivalDeadline: DateOnly
      CargoType: CargoType
      WeightKg: decimal }

module EstimateCreation =

    open FsToolkit.ErrorHandling

    /// Location.create は string エラーを返すため DomainError に持ち上げる。
    let private toLocation (field: string) (code: string) : Result<Location, DomainError> =
        Location.create code |> Result.mapError (fun msg -> ValidationError(field, msg))

    /// 見積を作成する。入力検証 → 集約生成 → 外部サービスでルート候補取得 → 候補反映 → 保存。
    let create
        (repo: EstimateRepository)
        (routing: ExternalRoutingServicePort)
        (newId: IdGenerator)
        (cmd: CreateEstimateCommand)
        : Async<Result<Estimate, DomainError>> =
        asyncResult {
            let! origin, destination, weight =
                validation {
                    let! origin = toLocation "Origin" cmd.OriginUnlocode
                    and! destination = toLocation "Destination" cmd.DestinationUnlocode
                    and! weight = WeightKg.create cmd.WeightKg
                    return origin, destination, weight
                }
                |> Result.mapError List.head

            let! estimate, _event = Estimate.create newId origin destination cmd.ArrivalDeadline cmd.CargoType weight

            let! candidates =
                routing.FetchCandidateRoutes
                    { Origin = origin
                      Destination = destination
                      ArrivalDeadline = cmd.ArrivalDeadline
                      CargoType = cmd.CargoType }

            let! withCandidates = Estimate.replaceCandidates candidates estimate
            do! repo.Save withCandidates
            return withCandidates
        }
