namespace CargoTracker.Booking.Application

open System
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain

// Booking コンテキストのアプリケーション層（US04: 貨物予約 / US05: 危険物・冷凍 / US06: 経路設計依頼）。
// 永続化・荷主存在確認は Port（関数レコード）で抽象化し、ドメインは純粋に保つ。

/// 貨物予約リポジトリの出力ポート（関数レコード）。テストは関数リテラルで差し替える。
type CargoRepository =
    { Save: Cargo -> Async<Result<unit, DomainError>>
      FindById: BookingId -> Async<Result<Cargo option, DomainError>> }

/// 荷主存在確認の ACL ポート（domain-model: ShipperId -> Async<bool>）。
/// Booking Context は Shipper Context に直接依存せず、このポート経由で荷主の存在を確認する。
type ShipperExistenceChecker =
    { Exists: ShipperId -> Async<Result<bool, DomainError>> }

/// 経路設計者への通知 ACL ポート（US06）。IT2 は関数リテラルのスタブ、後続で実装差し替え。
type RoutingRequestNotifier =
    { Notify: BookingId -> Async<Result<unit, DomainError>> }

/// 貨物種別の入力（UI からの DTO）。種別に応じて追加情報を持つ。
type CargoTypeInput =
    | GeneralInput
    | HazardousInput of hazardClass: string * unNumber: string * properShippingName: string
    | RefrigeratedInput of minTemperature: decimal * maxTemperature: decimal * unit: string

/// 荷受人の入力（任意）。
type ConsigneeInput =
    { Name: string
      Address: string
      ContactEmail: string }

/// 貨物予約登録コマンド（UI からの DTO）。
type BookCargoCommand =
    { ShipperId: string
      OriginUnlocode: string
      DestinationUnlocode: string
      ArrivalDeadline: DateOnly
      CargoType: CargoTypeInput
      WeightKg: decimal
      Consignee: ConsigneeInput option }

module BookCargo =

    open FsToolkit.ErrorHandling

    /// Location.create は string エラーを返すため DomainError に持ち上げる。
    let private toLocation (field: string) (code: string) : Result<Location, DomainError> =
        Location.create code |> Result.mapError (fun msg -> ValidationError(field, msg))

    /// 温度単位の文字列を DU に変換する。
    let private toTemperatureUnit (value: string) : Result<TemperatureUnit, DomainError> =
        match value.Trim().ToUpperInvariant() with
        | "CELSIUS"
        | "C" -> Ok Celsius
        | "FAHRENHEIT"
        | "F" -> Ok Fahrenheit
        | _ -> Error(ValidationError("TemperatureUnit", "温度単位は CELSIUS または FAHRENHEIT を指定してください。"))

    /// 種別入力を検証済み CargoType に変換する（危険物・冷凍は必須情報を検証）。
    let private validateCargoType (input: CargoTypeInput) : Result<CargoType, DomainError> =
        match input with
        | GeneralInput -> Ok General
        | HazardousInput(hazardClass, unNumber, properShippingName) ->
            HazardousDeclaration.create hazardClass unNumber properShippingName
            |> Result.map Hazardous
        | RefrigeratedInput(minTemperature, maxTemperature, unit) ->
            result {
                let! u = toTemperatureUnit unit
                let! req = TemperatureRequirement.create minTemperature maxTemperature u
                return Refrigerated req
            }

    /// 任意の荷受人入力を検証する。
    let private validateConsignee (input: ConsigneeInput option) : Result<Consignee option, DomainError> =
        match input with
        | None -> Ok None
        | Some c -> Consignee.create c.Name c.Address c.ContactEmail |> Result.map Some

    /// 貨物予約を登録する（US04/US05）。
    /// 入力検証 → 荷主存在確認（ACL）→ 集約生成 → 永続化 → イベント返却。
    let book
        (repo: CargoRepository)
        (shipperChecker: ShipperExistenceChecker)
        (newId: IdGenerator)
        (cmd: BookCargoCommand)
        : Async<Result<Cargo * BookingEvent list, DomainError>> =
        asyncResult {
            let! shipperId = ShipperId.ofString cmd.ShipperId

            let! origin, destination, weight, cargoType, consignee =
                validation {
                    let! origin = toLocation "Origin" cmd.OriginUnlocode
                    and! destination = toLocation "Destination" cmd.DestinationUnlocode
                    and! weight = Weight.create cmd.WeightKg
                    and! cargoType = validateCargoType cmd.CargoType
                    and! consignee = validateConsignee cmd.Consignee
                    return origin, destination, weight, cargoType, consignee
                }
                |> Result.mapError List.head

            let! routeSpec = RouteSpecification.create origin destination cmd.ArrivalDeadline

            // 荷主の存在を ACL 経由で確認する（存在しなければ NotFound）。
            let! exists = shipperChecker.Exists shipperId

            do!
                if exists then
                    Ok()
                else
                    Error(NotFound("Shipper", cmd.ShipperId))

            let! cargo, events = Cargo.book newId shipperId consignee routeSpec cargoType weight
            do! repo.Save cargo
            return cargo, events
        }

    /// 予約情報を経路設計者に引き渡す（US06）。
    /// 予約読込 → SubmitForRouting 遷移 → 永続化 → 経路設計者へ通知。
    let submitForRouting
        (repo: CargoRepository)
        (notifier: RoutingRequestNotifier)
        (bookingId: BookingId)
        : Async<Result<Cargo * BookingEvent list, DomainError>> =
        asyncResult {
            let! found = repo.FindById bookingId

            let! cargo =
                match found with
                | Some c -> Ok c
                | None -> Error(NotFound("Cargo", BookingId.value bookingId))

            let! updated, events = Cargo.execute cargo SubmitForRouting
            do! repo.Save updated
            do! notifier.Notify bookingId
            return updated, events
        }
