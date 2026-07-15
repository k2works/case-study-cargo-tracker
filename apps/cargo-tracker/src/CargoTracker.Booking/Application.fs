namespace CargoTracker.Booking.Application

open System
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain

// Booking コンテキストのアプリケーション層（US04: 貨物予約 / US05: 危険物・冷凍 / US06: 経路設計依頼）。
// 永続化・荷主存在確認は Port（関数レコード）で抽象化し、ドメインは純粋に保つ。

/// 貨物予約リポジトリの出力ポート（関数レコード）。テストは関数リテラルで差し替える。
type CargoRepository =
    { Save: Cargo -> Async<Result<unit, DomainError>>
      Update: Cargo -> Async<Result<unit, DomainError>>
      FindById: BookingId -> Async<Result<Cargo option, DomainError>> }

/// 荷主存在確認の ACL ポート（domain-model: ShipperId -> Async<bool>）。
/// Booking Context は Shipper Context に直接依存せず、このポート経由で荷主の存在を確認する。
type ShipperExistenceChecker =
    { Exists: ShipperId -> Async<Result<bool, DomainError>> }

/// 経路設計者への通知 ACL ポート（US06）。IT2 は関数リテラルのスタブ、後続で実装差し替え。
type RoutingRequestNotifier =
    { Notify: BookingId -> Async<Result<unit, DomainError>> }

/// 荷主への通知ポート（US12）。確定経路などを荷主に通知し、記録する。
/// recipient は荷主識別子、message は通知本文。実送信の有無は実装（アダプタ）で吸収する。
type ShipperNotifier =
    { Notify: BookingId -> string -> string -> Async<Result<unit, DomainError>> }

/// ドメインイベントの発行ポート（ADR-0002）。永続化コミット成功後にのみ発火する。
/// 他コンテキスト（Routing/Tracking 等）への連携はこのポートの実装で吸収する（BC 分離）。
type BookingEventDispatcher =
    { Dispatch: BookingEvent -> Async<unit> }

/// 何もしないイベントディスパッチャ（発火先が未接続の局面向け・テスト用）。
module NullBookingEventDispatcher =
    let create () : BookingEventDispatcher =
        { Dispatch = fun _ -> async { return () } }

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

    /// 種別入力を検証済み CargoType に変換する（危険物・冷凍は必須情報を検証）。
    /// 温度単位の文字列変換は Domain の `TemperatureUnit.ofString` に集約している（DRY）。
    let private validateCargoType (input: CargoTypeInput) : Result<CargoType, DomainError> =
        match input with
        | GeneralInput -> Ok General
        | HazardousInput(hazardClass, unNumber, properShippingName) ->
            HazardousDeclaration.create hazardClass unNumber properShippingName
            |> Result.map Hazardous
        | RefrigeratedInput(minTemperature, maxTemperature, unit) ->
            result {
                let! u = TemperatureUnit.ofString unit
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
            do! repo.Update updated
            do! notifier.Notify bookingId
            return updated, events
        }

/// 経路確定〜予約確定のワークフロー（US09/US11/US13）。
/// 予約読込 → コマンド実行（ドメイン検証） → 永続化、の共通形をとる。
module RouteAssignment =

    open FsToolkit.ErrorHandling

    /// 予約を読み込みコマンドを適用して永続化し、コミット成功後にイベントを発火する共通ワークフロー。
    /// `repo.Update` が Ok を返した時点で永続化はコミット済みのため、ここでの dispatch は post-commit（ADR-0002）。
    /// 失敗（NotFound・ドメイン検証エラー・永続化失敗）時はイベントを発火しない。
    let private applyCommand
        (repo: CargoRepository)
        (dispatcher: BookingEventDispatcher)
        (bookingId: BookingId)
        (command: BookingCommand)
        : Async<Result<Cargo * BookingEvent list, DomainError>> =
        asyncResult {
            let! found = repo.FindById bookingId

            let! cargo =
                match found with
                | Some c -> Ok c
                | None -> Error(NotFound("Cargo", BookingId.value bookingId))

            let! updated, events = Cargo.execute cargo command
            do! repo.Update updated
            // 永続化コミット後にのみイベントを順次発火する（ロールバック時は未発火）。
            for e in events do
                do! (dispatcher.Dispatch e |> Async.map Ok)

            return updated, events
        }

    /// 確定経路を予約に紐付ける（US09/US11・RoutingRequested → RouteProposed）。
    /// 旅程がルート仕様を満たすかはドメイン（Cargo.execute）が検証する。
    let proposeRoute
        (repo: CargoRepository)
        (dispatcher: BookingEventDispatcher)
        (bookingId: BookingId)
        (itinerary: CargoItinerary)
        =
        applyCommand repo dispatcher bookingId (ProposeRoute itinerary)

    /// 予約を確定する（US13・RouteProposed → Confirmed）。
    let confirmBooking (repo: CargoRepository) (dispatcher: BookingEventDispatcher) (bookingId: BookingId) =
        applyCommand repo dispatcher bookingId ConfirmBooking

    /// 経路設計中へ差し戻す（US13 受入条件4・Confirmed → RoutingRequested）。
    let restoreToRouting (repo: CargoRepository) (dispatcher: BookingEventDispatcher) (bookingId: BookingId) =
        applyCommand repo dispatcher bookingId RestoreToRouting

    /// 予約をキャンセルする（US13・任意状態 → Cancelled）。
    let cancel (repo: CargoRepository) (dispatcher: BookingEventDispatcher) (bookingId: BookingId) (reason: string) =
        applyCommand repo dispatcher bookingId (Cancel reason)

    /// 荷主に確定経路を通知する（US12）。
    /// 予約読込 → 確定経路（旅程）から通知本文を構成 → 荷主へ通知・記録。
    /// 旅程が未確定（RouteProposed/Confirmed 以外）の場合は業務ルール違反とする。
    let notifyRouteToShipper
        (repo: CargoRepository)
        (notifier: ShipperNotifier)
        (bookingId: BookingId)
        : Async<Result<unit, DomainError>> =
        asyncResult {
            let! found = repo.FindById bookingId

            let! cargo =
                match found with
                | Some c -> Ok c
                | None -> Error(NotFound("Cargo", BookingId.value bookingId))

            let! itinerary =
                match BookingState.itinerary cargo.State with
                | Some i -> Ok i
                | None -> Error(BusinessRuleViolation("ShipperNotification", "確定経路が無いため荷主に通知できません。"))

            let route =
                CargoItinerary.legs itinerary
                |> List.map (fun leg ->
                    sprintf "%s→%s" (Location.value (Leg.loadLocation leg)) (Location.value (Leg.unloadLocation leg)))
                |> String.concat " / "

            let recipient = (ShipperId.value cargo.ShipperId).ToString("D")
            let message = sprintf "予約 %s の確定経路: %s" (BookingId.value bookingId) route
            do! notifier.Notify bookingId recipient message
        }
