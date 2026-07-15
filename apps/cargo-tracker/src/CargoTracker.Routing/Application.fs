namespace CargoTracker.Routing.Application

open System
open CargoTracker.Shared.Domain
open CargoTracker.Routing.Domain

// Routing コンテキストのアプリケーション層（US24: 登録 / US25: 更新 / US07: 検索 / US08: 経路候補算出）。
// 永続化は Port（関数レコード）で抽象化し、ドメインは純粋に保つ。

/// 航海リポジトリの出力ポート（関数レコード）。テストは関数リテラルで差し替える。
type VoyageRepository =
    { Save: Voyage -> Async<Result<unit, DomainError>>
      Update: Voyage -> Async<Result<unit, DomainError>>
      FindByNumber: VoyageNumber -> Async<Result<Voyage option, DomainError>>
      FindAll: unit -> Async<Result<Voyage list, DomainError>> }

/// 運送区間の入力（UI からの DTO）。
type MovementInput =
    { DepartureUnlocode: string
      ArrivalUnlocode: string
      DepartureDate: DateTimeOffset
      ArrivalDate: DateTimeOffset }

/// 航海登録・更新コマンド（UI からの DTO）。
type VoyageCommand =
    { VoyageNumber: string
      VesselName: string
      CarrierName: string
      Movements: MovementInput list
      SupportedCargoTypes: string list }

module VoyageWorkflow =

    open FsToolkit.ErrorHandling

    let private toLocation (field: string) (code: string) : Result<Location, DomainError> =
        Location.create code |> Result.mapError (fun m -> ValidationError(field, m))

    /// 入力の運送区間列を検証済み CarrierMovement 列へ変換する（順序は入力順で 1 始まり）。
    let private validateMovements (inputs: MovementInput list) : Result<CarrierMovement list, DomainError> =
        inputs
        |> List.mapi (fun i m -> (i, m))
        |> List.traverseResultM (fun (i, m) ->
            result {
                let! dep = toLocation "DepartureLocation" m.DepartureUnlocode
                let! arr = toLocation "ArrivalLocation" m.ArrivalUnlocode
                return! CarrierMovement.create dep arr m.DepartureDate m.ArrivalDate (i + 1)
            })

    /// コマンドを検証済みの値オブジェクト群へ変換する。
    let private validateCommand (cmd: VoyageCommand) =
        result {
            let! voyageNumber = VoyageNumber.create cmd.VoyageNumber
            let! vessel = VesselName.create cmd.VesselName
            let! carrier = CarrierName.create cmd.CarrierName
            let! movements = validateMovements cmd.Movements
            let! schedule = Schedule.create movements

            let! tags = cmd.SupportedCargoTypes |> List.traverseResultM CargoTypeTag.ofString

            return voyageNumber, vessel, carrier, schedule, Set.ofList tags
        }

    /// コマンドの妥当性のみを検証する（US25 の差分確認前チェック用・永続化しない）。
    let validate (cmd: VoyageCommand) : Async<Result<unit, DomainError>> =
        async { return validateCommand cmd |> Result.map (fun _ -> ()) }

    /// 航海を新規登録する（US24）。航海番号の重複を確認し、登録する。
    let register (repo: VoyageRepository) (cmd: VoyageCommand) : Async<Result<Voyage, DomainError>> =
        asyncResult {
            let! voyageNumber, vessel, carrier, schedule, tags = validateCommand cmd
            let! existing = repo.FindByNumber voyageNumber

            do!
                match existing with
                | Some _ -> Error(BusinessRuleViolation("VoyageNumber", "同一の航海番号が既に登録されています。"))
                | None -> Ok()

            // NOTE（IT3 バイパス中・レビュー M2/IT2 H6）: VoyageEvent は現時点で消費者が無いため
            // 破棄している。IT4 で Booking との経路確定連携を実装する際に UnitOfWork の post-commit
            // ディスパッチ（ADR-0002）へ結線する（retrospective-3 Try#1）。
            let! voyage, _events = Voyage.register voyageNumber vessel carrier schedule tags
            do! repo.Save voyage
            return voyage
        }

    /// 既存航海を更新する（US25）。航海番号で既存を取得し、内容を上書きする。
    let update (repo: VoyageRepository) (cmd: VoyageCommand) : Async<Result<Voyage, DomainError>> =
        asyncResult {
            let! voyageNumber, vessel, carrier, schedule, tags = validateCommand cmd
            let! found = repo.FindByNumber voyageNumber

            let! existing =
                match found with
                | Some v -> Ok v
                | None -> Error(NotFound("Voyage", VoyageNumber.value voyageNumber))

            let! updated, _events = Voyage.update vessel carrier schedule tags existing
            do! repo.Update updated
            return updated
        }

    /// 航海スケジュールを検索する（US07）。出発地・目的地・貨物種別で絞り込む。
    let search
        (repo: VoyageRepository)
        (origin: Location)
        (destination: Location)
        (cargoType: CargoTypeTag)
        : Async<Result<Voyage list, DomainError>> =
        asyncResult {
            let! all = repo.FindAll()

            return
                all
                |> List.filter (fun v ->
                    Voyage.supports cargoType v
                    && Location.sameAs (Schedule.origin v.Schedule) origin
                    && Location.sameAs (Schedule.destination v.Schedule) destination)
        }

    /// 経路候補を算出する（US08）。全航海を取得し RouteComputation で候補を構成する。
    let computeRoutes (repo: VoyageRepository) (query: RouteQuery) : Async<Result<RouteCandidate list, DomainError>> =
        asyncResult {
            let! all = repo.FindAll()
            return RouteComputation.computeCandidates all query
        }
