module CargoTracker.Web.App

open System.Data
open System.Security.Claims
open Microsoft.AspNetCore.Authentication
open Microsoft.AspNetCore.Authentication.Cookies
open Microsoft.AspNetCore.Builder
open Microsoft.AspNetCore.Http
open Microsoft.Extensions.Configuration
open Microsoft.Extensions.DependencyInjection
open Giraffe

// ASP.NET Core Cookie 認証の Web 配線（ADR-0005）。
// パイプライン構成を関数として切り出し、本番ホストとテストサーバーの双方から利用する。

/// 接続ファクトリ（DI 登録）。呼ぶたびに開いた接続を返す。
type ConnectionFactory = unit -> IDbConnection

/// ClaimsPrincipal からロール（ROLE_*）を取り出す。
let rolesOf (ctx: HttpContext) : string list =
    ctx.User.FindAll(ClaimTypes.Role) |> Seq.map (fun c -> c.Value) |> List.ofSeq

/// 合成ルートで使う実時刻ポート（ADR-0006）。
let systemClock: CargoTracker.Shared.Domain.Clock =
    fun () -> System.DateTimeOffset.Now

/// 合成ルートで使う GUID 生成ポート（ADR-0006）。
let systemNewId: CargoTracker.Shared.Domain.IdGenerator =
    fun () -> System.Guid.NewGuid()

/// ドメインエラーを日本語のユーザー向けメッセージに変換する。
let domainErrorMessage (err: CargoTracker.Shared.Domain.DomainError) : string =
    match err with
    | CargoTracker.Shared.Domain.ValidationError(_, message) -> message
    | CargoTracker.Shared.Domain.BusinessRuleViolation(_, message) -> message
    | CargoTracker.Shared.Domain.InvalidStateTransition(cur, attempted) ->
        sprintf "現在の状態（%s）では操作（%s）を実行できません。" cur attempted
    | CargoTracker.Shared.Domain.NotFound(entity, id) -> sprintf "%s が見つかりません（%s）。" entity id

/// 認証済みユーザーを Cookie にサインインさせる（ユーザー名 + ロールクレーム）。
let private signIn (ctx: HttpContext) (user: Auth.AuthenticatedUser) =
    task {
        let claims =
            Claim(ClaimTypes.Name, user.Username)
            :: (user.Roles |> List.map (fun r -> Claim(ClaimTypes.Role, r)))

        let identity =
            ClaimsIdentity(claims, CookieAuthenticationDefaults.AuthenticationScheme)

        let principal = ClaimsPrincipal(identity)
        do! ctx.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, principal)
    }

/// ログイン POST: フォームを検証し、認証成功で Cookie 発行 + ホームへ、失敗で再表示。
let private loginPost: HttpHandler =
    fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let username = string form.["username"]
            let password = string form.["password"]
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()
            let store = Auth.UserStore.create conn

            match Auth.authenticate store username password with
            | Some user ->
                do! signIn ctx user
                return! redirectTo false "/" next ctx
            | None ->
                ctx.SetStatusCode 401

                return!
                    htmlView
                        (Views.login username "" Seed.defaultUsernames (Some "ユーザー ID またはパスワードが正しくありません。"))
                        next
                        ctx
        }

/// ログアウト: Cookie を破棄しログイン画面へ戻す。
let private logout: HttpHandler =
    fun next ctx ->
        task {
            do! ctx.SignOutAsync(CookieAuthenticationDefaults.AuthenticationScheme)
            return! redirectTo false "/login" next ctx
        }

/// 未認証時はログイン画面へリダイレクトする。
let private mustBeLoggedIn: HttpHandler =
    requiresAuthentication (redirectTo false "/login")

/// ダッシュボード（認証必須・ロールで表示制御）。
let private dashboard: HttpHandler =
    mustBeLoggedIn
    >=> fun next ctx -> htmlView (Views.dashboard (rolesOf ctx)) next ctx

/// 指定ロールを要求する（不足時は 403）。認証は前段の mustBeLoggedIn で担保する。
let private mustHaveRole (role: string) : HttpHandler =
    mustBeLoggedIn >=> requiresRole role (setStatusCode 403 >=> text "権限がありません。")

/// いずれかのロールを要求する（不足時は 403）。
let private mustHaveAnyRole (roles: string list) : HttpHandler =
    mustBeLoggedIn >=> requiresRoleOf roles (setStatusCode 403 >=> text "権限がありません。")

/// 準備中プレースホルダ画面のハンドラ（ウォーキングスケルトンの骨格）。
let private placeholder (title: string) (allowed: string list) : HttpHandler =
    mustHaveAnyRole allowed
    >=> fun next ctx -> htmlView (Views.placeholder title (rolesOf ctx)) next ctx

// ---- US02/US03: 荷主管理（ROLE_SALES）----

let private shipperList: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()
        let items = CargoTracker.Shipper.Infrastructure.ShipperQueries.findAll conn

        let rows =
            items
            |> List.map (fun i ->
                { Views.Code = i.Code
                  Views.Name = i.Name
                  Views.Email = i.Email
                  Views.Kind = (if i.ShipperType = "CORPORATE" then "法人" else "個人")
                  Views.DiscountRate = i.DiscountRate })

        htmlView (Views.shipperList (rolesOf ctx) rows) next ctx

let private shipperNew: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx -> htmlView (Views.shipperForm (rolesOf ctx) Views.emptyShipperForm None) next ctx

/// フォーム値を読み取り、登録コマンドと再表示用の値を組み立てる。
let private readShipperForm (form: Microsoft.AspNetCore.Http.IFormCollection) =
    let get key = string form.[key]
    let isCorporate = (get "isCorporate") = "true"

    let values: Views.ShipperFormValues =
        { Name = get "name"
          Email = get "email"
          Phone = get "phone"
          Address = get "address"
          IsCorporate = isCorporate
          ContractNumber = get "contractNumber"
          DiscountRatePercent = get "discountRatePercent" }

    let corporate =
        if isCorporate then
            let rate =
                match System.Decimal.TryParse(get "discountRatePercent") with
                | true, p -> p / 100m
                | _ -> -1m // 範囲外にしてドメイン検証で弾く

            Some
                { CargoTracker.Shipper.Application.ContractNumber = get "contractNumber"
                  CargoTracker.Shipper.Application.DiscountRate = rate }
        else
            None

    let cmd: CargoTracker.Shipper.Application.RegisterShipperCommand =
        { Name = get "name"
          Email = get "email"
          Phone = (if get "phone" = "" then None else Some(get "phone"))
          Address = (if get "address" = "" then None else Some(get "address"))
          Corporate = corporate }

    values, cmd

let private shipperCreate: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let values, cmd = readShipperForm form
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Shipper.Infrastructure.ShipperRepository.create conn systemClock

            let newId: CargoTracker.Shared.Domain.IdGenerator = fun () -> System.Guid.NewGuid()
            let! result = CargoTracker.Shipper.Application.ShipperRegistration.register repo newId cmd

            match result with
            | Ok _ -> return! redirectTo false "/shippers" next ctx
            | Error err ->
                ctx.SetStatusCode 400
                let msg = domainErrorMessage err
                return! htmlView (Views.shipperForm (rolesOf ctx) values (Some msg)) next ctx
        }

// ---- US01: 見積管理（ROLE_SALES）----

let private estimateList: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()
        let items = CargoTracker.Estimation.Infrastructure.EstimateQueries.findAll conn

        let rows =
            items
            |> List.map (fun i ->
                { Views.EstimateRow.EstimateId = i.EstimateId
                  Views.EstimateRow.Origin = i.Origin
                  Views.EstimateRow.Destination = i.Destination
                  Views.EstimateRow.ArrivalDeadline = i.ArrivalDeadline
                  Views.EstimateRow.CargoType = i.CargoType
                  Views.EstimateRow.WeightKg = i.WeightKg
                  Views.EstimateRow.Status = i.Status
                  Views.EstimateRow.CandidateCount = i.CandidateCount })

        htmlView (Views.estimateList (rolesOf ctx) rows) next ctx

let private estimateNew: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx -> htmlView (Views.estimateForm (rolesOf ctx) Views.emptyEstimateForm None) next ctx

// ---- US04: 貨物予約一覧（ROLE_SALES / ROLE_SHIPPER）----

/// 荷主 uuid → 名称 の解決マップを構築する（ADR-0008・BC 分離を合成層で結合）。
let private shipperNameMap (conn: IDbConnection) : Map<string, string> =
    CargoTracker.Shipper.Infrastructure.ShipperQueries.findAllForSelection conn
    |> List.map (fun s -> s.Uuid, s.Name)
    |> Map.ofList

/// shipper_id（uuid）を荷主名に解決する。未解決時は uuid を短縮表示する。
let private resolveShipperName (names: Map<string, string>) (shipperId: string) : string =
    match Map.tryFind shipperId names with
    | Some name -> name
    | None ->
        if shipperId.Length >= 8 then
            sprintf "(未登録: %s…)" (shipperId.Substring(0, 8))
        else
            sprintf "(未登録: %s)" shipperId

/// 貨物予約一覧（`/bookings`）。IT1 ウォーキングスケルトンのプレースホルダを実画面へ差し替え。
let private bookingList: HttpHandler =
    mustHaveAnyRole [ "ROLE_SALES"; "ROLE_SHIPPER" ]
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()
        let items = CargoTracker.Booking.Infrastructure.CargoQueries.findAll conn
        // 荷主名は uuid→name の解決マップで補う（ADR-0008・BC 分離を合成層で結合）。
        let shipperNames = shipperNameMap conn

        let rows =
            items
            |> List.map (fun i ->
                { Views.CargoRow.BookingId = i.BookingId
                  Views.CargoRow.ShipperName = resolveShipperName shipperNames i.ShipperId
                  Views.CargoRow.CargoType = i.CargoType
                  Views.CargoRow.Origin = i.Origin
                  Views.CargoRow.Destination = i.Destination
                  Views.CargoRow.ArrivalDeadline = i.ArrivalDeadline
                  Views.CargoRow.BookingStatus = i.BookingStatus })

        htmlView (Views.bookingList (rolesOf ctx) rows) next ctx

/// 荷主選択肢を読み込む（貨物予約フォーム用・ADR-0008）。
let private loadShipperChoices (conn: IDbConnection) : Views.ShipperChoice list =
    CargoTracker.Shipper.Infrastructure.ShipperQueries.findAllForSelection conn
    |> List.map (fun s ->
        { Views.ShipperChoice.Uuid = s.Uuid
          Views.ShipperChoice.Label = sprintf "%s（%s）" s.Name s.Code })

/// 貨物予約登録フォーム（`/bookings/new`・US04/US05）。
let private bookingNew: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()
        let shippers = loadShipperChoices conn
        htmlView (Views.bookingForm (rolesOf ctx) shippers Views.emptyBookingForm None) next ctx

/// フォーム入力から CargoTypeInput を組み立てる（未入力はドメイン層で検証・弾く）。
let private buildCargoTypeInput (get: string -> string) : CargoTracker.Booking.Application.CargoTypeInput =
    match get "cargoType" with
    | "HAZARDOUS" ->
        CargoTracker.Booking.Application.HazardousInput(get "hazardClass", get "unNumber", get "properShippingName")
    | "REFRIGERATED" ->
        let parseDec s =
            match System.Decimal.TryParse(s: string) with
            | true, v -> v
            | _ -> 0m

        CargoTracker.Booking.Application.RefrigeratedInput(
            parseDec (get "minTemperature"),
            parseDec (get "maxTemperature"),
            get "temperatureUnit"
        )
    | _ -> CargoTracker.Booking.Application.GeneralInput

/// 貨物予約登録の実行（`POST /bookings`・US04/US05）。
let private bookingCreate: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get key = string form.[key]

            let values: Views.BookingFormValues =
                { ShipperId = get "shipperId"
                  OriginUnlocode = get "originUnlocode"
                  DestinationUnlocode = get "destinationUnlocode"
                  ArrivalDeadline = get "arrivalDeadline"
                  CargoType = get "cargoType"
                  WeightKg = get "weightKg"
                  HazardClass = get "hazardClass"
                  UnNumber = get "unNumber"
                  ProperShippingName = get "properShippingName"
                  MinTemperature = get "minTemperature"
                  MaxTemperature = get "maxTemperature"
                  TemperatureUnit = get "temperatureUnit" }

            let weight =
                match System.Decimal.TryParse(get "weightKg") with
                | true, w -> w
                | _ -> -1m // 範囲外にしてドメイン検証で弾く

            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let renderError (msg: string) =
                ctx.SetStatusCode 400
                let shippers = loadShipperChoices conn
                htmlView (Views.bookingForm (rolesOf ctx) shippers values (Some msg)) next ctx

            // 到着期限のパース失敗は「今日」に化けさせず、明示的な検証エラーとして扱う。
            match System.DateOnly.TryParse(get "arrivalDeadline") with
            | false, _ -> return! renderError "希望到着期限の形式が不正です。"
            | true, deadline ->
                let cmd: CargoTracker.Booking.Application.BookCargoCommand =
                    { ShipperId = get "shipperId"
                      OriginUnlocode = get "originUnlocode"
                      DestinationUnlocode = get "destinationUnlocode"
                      ArrivalDeadline = deadline
                      CargoType = buildCargoTypeInput get
                      WeightKg = weight
                      Consignee = None }

                let repo =
                    CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

                let shipperChecker =
                    CargoTracker.Booking.Infrastructure.ShipperExistenceAdapter.create conn

                let newId: CargoTracker.Shared.Domain.IdGenerator = fun () -> System.Guid.NewGuid()
                let! result = CargoTracker.Booking.Application.BookCargo.book repo shipperChecker newId cmd

                match result with
                | Ok(cargo, _) ->
                    // PRG: 作成した予約の詳細へリダイレクト（Location に booking_id が入る）。
                    let bookingId = CargoTracker.Booking.Domain.BookingId.value cargo.BookingId
                    return! redirectTo false (sprintf "/bookings/%s" bookingId) next ctx
                | Error err -> return! renderError (domainErrorMessage err)
        }

// ---- US06: 予約詳細・経路設計依頼 ----

/// Cargo 集約を詳細表示用の DTO へ射影する。荷主名は解決マップで補う（ADR-0008）。
let private toBookingDetail
    (shipperNames: Map<string, string>)
    (cargo: CargoTracker.Booking.Domain.Cargo)
    : Views.BookingDetail =
    let cargoTypeStr =
        match cargo.CargoType with
        | CargoTracker.Booking.Domain.General -> "GENERAL"
        | CargoTracker.Booking.Domain.Hazardous _ -> "HAZARDOUS"
        | CargoTracker.Booking.Domain.Refrigerated _ -> "REFRIGERATED"

    let spec = cargo.RouteSpecification

    let shipperUuid =
        (CargoTracker.Shared.Domain.ShipperId.value cargo.ShipperId).ToString("D")

    { BookingId = CargoTracker.Booking.Domain.BookingId.value cargo.BookingId
      ShipperName = resolveShipperName shipperNames shipperUuid
      CargoType = cargoTypeStr
      Origin = CargoTracker.Shared.Domain.Location.value (CargoTracker.Booking.Domain.RouteSpecification.origin spec)
      Destination =
        CargoTracker.Shared.Domain.Location.value (CargoTracker.Booking.Domain.RouteSpecification.destination spec)
      ArrivalDeadline = (CargoTracker.Booking.Domain.RouteSpecification.arrivalDeadline spec).ToString("yyyy-MM-dd")
      Weight = sprintf "%M" (CargoTracker.Booking.Domain.Weight.value cargo.Weight)
      BookingStatus = CargoTracker.Booking.Domain.BookingState.toString cargo.State
      CanSubmitRouting = (cargo.State = CargoTracker.Booking.Domain.Preliminary)
      Itinerary =
        match CargoTracker.Booking.Domain.BookingState.itinerary cargo.State with
        | Some itin ->
            CargoTracker.Booking.Domain.CargoItinerary.legs itin
            |> List.map (fun leg ->
                sprintf
                    "%s: %s → %s（%s 〜 %s）"
                    (CargoTracker.Booking.Domain.VoyageNumber.value (CargoTracker.Booking.Domain.Leg.voyage leg))
                    (CargoTracker.Shared.Domain.Location.value (CargoTracker.Booking.Domain.Leg.loadLocation leg))
                    (CargoTracker.Shared.Domain.Location.value (CargoTracker.Booking.Domain.Leg.unloadLocation leg))
                    ((CargoTracker.Booking.Domain.Leg.loadTime leg).ToString("yyyy-MM-dd"))
                    ((CargoTracker.Booking.Domain.Leg.unloadTime leg).ToString("yyyy-MM-dd")))
        | None -> []
      CanConfirm =
        (match cargo.State with
         | CargoTracker.Booking.Domain.RouteProposed _ -> true
         | _ -> false)
      CanRestore =
        (match cargo.State with
         | CargoTracker.Booking.Domain.Confirmed _ -> true
         | _ -> false)
      CanCancel =
        (match cargo.State with
         | CargoTracker.Booking.Domain.Cancelled _ -> false
         | _ -> true)
      // US12 の荷主通知は経路提案中（RouteProposed）のみ。確定後の重複通知は業務上不自然（レビュー M1）。
      CanNotify =
        (match cargo.State with
         | CargoTracker.Booking.Domain.RouteProposed _ -> true
         | _ -> false) }

/// 貨物予約詳細（`GET /bookings/{bookingId}`・US06）。
let private bookingDetail (bookingIdStr: string) : HttpHandler =
    mustHaveAnyRole [ "ROLE_SALES"; "ROLE_SHIPPER" ]
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

            let bookingId = CargoTracker.Booking.Domain.BookingId.ofString bookingIdStr
            let! found = repo.FindById bookingId
            let shipperNames = shipperNameMap conn

            // PRG 後の操作成功メッセージ（?msg=... クエリ・レビュー H2）。
            let info =
                match ctx.TryGetQueryStringValue "msg" with
                | Some "routed" -> Some "確定経路を予約に紐付けました。"
                | Some "notified" -> Some "荷主に確定経路を通知しました。"
                | Some "confirmed" -> Some "予約を確定しました。"
                | Some "restored" -> Some "経路設計へ差し戻しました。"
                | Some "cancelled" -> Some "予約をキャンセルしました。"
                | _ -> None

            match found with
            | Ok(Some cargo) ->
                return! htmlView (Views.bookingDetail (rolesOf ctx) (toBookingDetail shipperNames cargo) info) next ctx
            | Ok None -> return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 経路設計依頼（`POST /bookings/{bookingId}/routing`・US06）。
let private bookingSubmitRouting (bookingIdStr: string) : HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

            let notifier =
                CargoTracker.Booking.Infrastructure.StubRoutingRequestNotifier.create ()

            let bookingId = CargoTracker.Booking.Domain.BookingId.ofString bookingIdStr
            let! result = CargoTracker.Booking.Application.BookCargo.submitForRouting repo notifier bookingId

            match result with
            | Ok _ -> return! redirectTo false (sprintf "/bookings/%s" bookingIdStr) next ctx
            | Error(CargoTracker.Shared.Domain.NotFound _) ->
                return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 予約確定・差し戻し・キャンセルの共通ハンドラ（US13・ROLE_SALES）。
/// RouteAssignment の各ワークフローを受け取り、PRG で予約詳細へ戻す。
let private bookingStateAction
    (workflow:
        CargoTracker.Booking.Application.CargoRepository
            -> CargoTracker.Booking.Application.BookingEventDispatcher
            -> CargoTracker.Booking.Domain.BookingId
            -> Async<
                Result<
                    CargoTracker.Booking.Domain.Cargo * CargoTracker.Booking.Domain.BookingEvent list,
                    CargoTracker.Shared.Domain.DomainError
                 >
             >)
    (msgCode: string)
    (bookingIdStr: string)
    : HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

            // 実消費ディスパッチャ: BookingConfirmed → 追跡番号自動発行（US14・retro-4 Try#1）。
            let dispatcher =
                CargoTracker.Web.BookingEventConsumer.create conn systemClock systemNewId

            let bookingId = CargoTracker.Booking.Domain.BookingId.ofString bookingIdStr
            let! result = workflow repo dispatcher bookingId

            match result with
            // PRG: 成功メッセージコードを付けて予約詳細へ戻す（レビュー H2）。
            | Ok _ -> return! redirectTo false (sprintf "/bookings/%s?msg=%s" bookingIdStr msgCode) next ctx
            | Error(CargoTracker.Shared.Domain.NotFound _) ->
                return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 予約確定（`POST /bookings/{bookingId}/confirm`・US13）。
let private bookingConfirm (bookingIdStr: string) : HttpHandler =
    bookingStateAction CargoTracker.Booking.Application.RouteAssignment.confirmBooking "confirmed" bookingIdStr

/// 経路設計へ差し戻し（`POST /bookings/{bookingId}/restore`・US13 受入条件4）。
let private bookingRestore (bookingIdStr: string) : HttpHandler =
    bookingStateAction CargoTracker.Booking.Application.RouteAssignment.restoreToRouting "restored" bookingIdStr

/// 予約キャンセル（`POST /bookings/{bookingId}/cancel`・US13）。
/// キャンセル後に荷主へキャンセル確認通知を送る（US13 受入基準6）。
let private bookingCancel (bookingIdStr: string) : HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

            let dispatcher =
                CargoTracker.Booking.Infrastructure.StubBookingEventDispatcher.create ()

            let notifier =
                CargoTracker.Booking.Infrastructure.NotificationLogShipperNotifier.create conn systemClock

            let bookingId = CargoTracker.Booking.Domain.BookingId.ofString bookingIdStr

            let! result =
                CargoTracker.Booking.Application.RouteAssignment.cancelAndNotify
                    repo
                    dispatcher
                    notifier
                    bookingId
                    "営業によるキャンセル"

            match result with
            | Ok _ -> return! redirectTo false (sprintf "/bookings/%s?msg=cancelled" bookingIdStr) next ctx
            | Error(CargoTracker.Shared.Domain.NotFound _) ->
                return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 荷主への経路通知（`POST /bookings/{bookingId}/notify`・US12・ROLE_SALES）。
let private bookingNotify (bookingIdStr: string) : HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

            let notifier =
                CargoTracker.Booking.Infrastructure.NotificationLogShipperNotifier.create conn systemClock

            let bookingId = CargoTracker.Booking.Domain.BookingId.ofString bookingIdStr

            let! result = CargoTracker.Booking.Application.RouteAssignment.notifyRouteToShipper repo notifier bookingId

            match result with
            | Ok _ -> return! redirectTo false (sprintf "/bookings/%s?msg=notified" bookingIdStr) next ctx
            | Error(CargoTracker.Shared.Domain.NotFound _) ->
                return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

let private parseCargoType (value: string) : CargoTracker.Estimation.Domain.CargoType =
    match value with
    | "HAZARDOUS" -> CargoTracker.Estimation.Domain.Hazardous
    | "REFRIGERATED" -> CargoTracker.Estimation.Domain.Refrigerated
    | _ -> CargoTracker.Estimation.Domain.General

let private estimateCreate: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get key = string form.[key]

            let values: Views.EstimateFormValues =
                { OriginUnlocode = get "originUnlocode"
                  DestinationUnlocode = get "destinationUnlocode"
                  ArrivalDeadline = get "arrivalDeadline"
                  CargoType = get "cargoType"
                  WeightKg = get "weightKg" }

            let deadline =
                match System.DateOnly.TryParse(get "arrivalDeadline") with
                | true, d -> d
                | _ -> System.DateOnly.FromDateTime(System.DateTime.Today)

            let weight =
                match System.Decimal.TryParse(get "weightKg") with
                | true, w -> w
                | _ -> -1m // 範囲外にしてドメイン検証で弾く

            let cmd: CargoTracker.Estimation.Application.CreateEstimateCommand =
                { OriginUnlocode = get "originUnlocode"
                  DestinationUnlocode = get "destinationUnlocode"
                  ArrivalDeadline = deadline
                  CargoType = parseCargoType (get "cargoType")
                  WeightKg = weight }

            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Estimation.Infrastructure.EstimateRepository.create conn systemClock

            let routing = CargoTracker.Estimation.Infrastructure.StubRoutingService.create ()
            let newId: CargoTracker.Shared.Domain.IdGenerator = fun () -> System.Guid.NewGuid()
            let! result = CargoTracker.Estimation.Application.EstimateCreation.create repo routing newId cmd

            match result with
            | Ok _ -> return! redirectTo false "/estimates" next ctx
            | Error err ->
                ctx.SetStatusCode 400
                return! htmlView (Views.estimateForm (rolesOf ctx) values (Some(domainErrorMessage err))) next ctx
        }

// ---- US24/US25/US07: 航路管理（ROLE_ROUTE_DESIGNER）----

/// Voyage 集約を一覧行 DTO へ射影する。
let private toVoyageRow (v: CargoTracker.Routing.Domain.Voyage) : Views.VoyageRow =
    let sched = v.Schedule
    let fmt (d: System.DateTimeOffset) = d.ToString("yyyy-MM-dd")

    { VoyageNumber = CargoTracker.Routing.Domain.VoyageNumber.value v.VoyageNumber
      Vessel = CargoTracker.Routing.Domain.VesselName.value v.Vessel
      Carrier = CargoTracker.Routing.Domain.CarrierName.value v.Carrier
      Origin = CargoTracker.Shared.Domain.Location.value (CargoTracker.Routing.Domain.Schedule.origin sched)
      Destination = CargoTracker.Shared.Domain.Location.value (CargoTracker.Routing.Domain.Schedule.destination sched)
      Departure = fmt (CargoTracker.Routing.Domain.Schedule.departureDate sched)
      Arrival = fmt (CargoTracker.Routing.Domain.Schedule.arrivalDate sched) }

/// 航路一覧（`/voyages`・US07/US24）。IT3 ウォーキングスケルトンのプレースホルダを実画面へ差し替え。
let private voyageList: HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Routing.Infrastructure.VoyageRepository.create conn systemClock

            let! result = repo.FindAll()

            let rows =
                match result with
                | Ok voyages -> voyages |> List.map toVoyageRow
                | Error _ -> []

            return! htmlView (Views.voyageList (rolesOf ctx) rows) next ctx
        }

/// 航海登録フォーム（`/voyages/new`・US24）。
let private voyageNew: HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        htmlView (Views.voyageForm (rolesOf ctx) "航海スケジュール登録" "/voyages" false Views.emptyVoyageForm None) next ctx

/// フォームから VoyageCommand を組み立てる（空の運送区間行は除外）。
let private readVoyageForm
    (get: string -> string)
    : Views.VoyageFormValues * CargoTracker.Routing.Application.VoyageCommand =
    let legRows =
        [ 1; 2; 3 ]
        |> List.map (fun n ->
            get (sprintf "leg%dDep" n),
            get (sprintf "leg%dArr" n),
            get (sprintf "leg%dDepDate" n),
            get (sprintf "leg%dArrDate" n))

    let values: Views.VoyageFormValues =
        { VoyageNumber = get "voyageNumber"
          VesselName = get "vesselName"
          CarrierName = get "carrierName"
          CargoGeneral = get "cargoGeneral" = "true"
          CargoHazardous = get "cargoHazardous" = "true"
          CargoRefrigerated = get "cargoRefrigerated" = "true"
          Legs = legRows }

    let parseDate (s: string) =
        match System.DateTimeOffset.TryParse s with
        | true, d -> d
        | _ -> System.DateTimeOffset.MinValue

    let movements =
        legRows
        |> List.filter (fun (dep, arr, _, _) -> dep <> "" && arr <> "")
        |> List.map (fun (dep, arr, depDate, arrDate) ->
            { CargoTracker.Routing.Application.DepartureUnlocode = dep
              CargoTracker.Routing.Application.ArrivalUnlocode = arr
              CargoTracker.Routing.Application.DepartureDate = parseDate depDate
              CargoTracker.Routing.Application.ArrivalDate = parseDate arrDate })

    let tags =
        [ (values.CargoGeneral, "GENERAL")
          (values.CargoHazardous, "HAZARDOUS")
          (values.CargoRefrigerated, "REFRIGERATED") ]
        |> List.filter fst
        |> List.map snd

    let cmd: CargoTracker.Routing.Application.VoyageCommand =
        { VoyageNumber = get "voyageNumber"
          VesselName = get "vesselName"
          CarrierName = get "carrierName"
          Movements = movements
          SupportedCargoTypes = tags }

    values, cmd

/// 航海登録の実行（`POST /voyages`・US24）。
let private voyageCreate: HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get key = string form.[key]
            let values, cmd = readVoyageForm get
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Routing.Infrastructure.VoyageRepository.create conn systemClock

            let! result = CargoTracker.Routing.Application.VoyageWorkflow.register repo cmd

            match result with
            | Ok _ -> return! redirectTo false "/voyages" next ctx
            | Error err ->
                ctx.SetStatusCode 400

                return!
                    htmlView
                        (Views.voyageForm
                            (rolesOf ctx)
                            "航海スケジュール登録"
                            "/voyages"
                            false
                            values
                            (Some(domainErrorMessage err)))
                        next
                        ctx
        }

/// Voyage 集約を編集フォーム値へ射影する（US25）。
let private toVoyageFormValues (v: CargoTracker.Routing.Domain.Voyage) : Views.VoyageFormValues =
    let fmt (d: System.DateTimeOffset) = d.ToString("yyyy-MM-ddTHH:mm")

    let legs =
        CargoTracker.Routing.Domain.Schedule.movements v.Schedule
        |> List.map (fun m ->
            CargoTracker.Shared.Domain.Location.value (CargoTracker.Routing.Domain.CarrierMovement.departureLocation m),
            CargoTracker.Shared.Domain.Location.value (CargoTracker.Routing.Domain.CarrierMovement.arrivalLocation m),
            fmt (CargoTracker.Routing.Domain.CarrierMovement.departureDate m),
            fmt (CargoTracker.Routing.Domain.CarrierMovement.arrivalDate m))

    // フォームは 3 区間固定のため、不足分を空行で埋める。
    let paddedLegs =
        legs @ List.replicate (max 0 (3 - List.length legs)) ("", "", "", "")

    let supports tag =
        CargoTracker.Routing.Domain.Voyage.supports tag v

    { VoyageNumber = CargoTracker.Routing.Domain.VoyageNumber.value v.VoyageNumber
      VesselName = CargoTracker.Routing.Domain.VesselName.value v.Vessel
      CarrierName = CargoTracker.Routing.Domain.CarrierName.value v.Carrier
      CargoGeneral = supports CargoTracker.Routing.Domain.General
      CargoHazardous = supports CargoTracker.Routing.Domain.Hazardous
      CargoRefrigerated = supports CargoTracker.Routing.Domain.Refrigerated
      Legs = paddedLegs }

/// 航海更新フォーム（`GET /voyages/{voyageNumber}/edit`・US25）。
let private voyageEdit (voyageNumberStr: string) : HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Routing.Infrastructure.VoyageRepository.create conn systemClock

            let vn = CargoTracker.Routing.Domain.VoyageNumber.ofString voyageNumberStr
            let! found = repo.FindByNumber vn

            match found with
            | Ok(Some voyage) ->
                let action = sprintf "/voyages/%s/edit" voyageNumberStr

                return!
                    htmlView
                        (Views.voyageForm (rolesOf ctx) "航海スケジュール更新" action true (toVoyageFormValues voyage) None)
                        next
                        ctx
            | Ok None -> return! (setStatusCode 404 >=> text "航海が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 航海更新の差分確認（`POST /voyages/{voyageNumber}/edit`・US25 受入条件2）。
/// 即時更新せず、既存内容と入力内容の差分を提示する。確定は POST /confirm で行う。
let private voyageUpdate (voyageNumberStr: string) : HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get key = string form.[key]
            let newValues, cmd = readVoyageForm get
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Routing.Infrastructure.VoyageRepository.create conn systemClock

            let vn = CargoTracker.Routing.Domain.VoyageNumber.ofString voyageNumberStr
            let action = sprintf "/voyages/%s/edit" voyageNumberStr

            // 入力の妥当性を先に検証（不正なら差分に進まずフォームへ戻す）。
            let! validation = CargoTracker.Routing.Application.VoyageWorkflow.validate cmd

            match validation with
            | Error err ->
                ctx.SetStatusCode 400

                return!
                    htmlView
                        (Views.voyageForm
                            (rolesOf ctx)
                            "航海スケジュール更新"
                            action
                            true
                            newValues
                            (Some(domainErrorMessage err)))
                        next
                        ctx
            | Ok() ->
                let! found = repo.FindByNumber vn

                match found with
                | Ok(Some existing) ->
                    let oldValues = toVoyageFormValues existing
                    return! htmlView (Views.voyageDiff (rolesOf ctx) voyageNumberStr oldValues newValues) next ctx
                | Ok None -> return! (setStatusCode 404 >=> text "航海が見つかりません。") next ctx
                | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 航海更新の確定（`POST /voyages/{voyageNumber}/confirm`・US25）。差分確認後の上書き更新。
let private voyageConfirm (voyageNumberStr: string) : HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get key = string form.[key]
            let values, cmd = readVoyageForm get
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Routing.Infrastructure.VoyageRepository.create conn systemClock

            let! result = CargoTracker.Routing.Application.VoyageWorkflow.update repo cmd

            match result with
            | Ok _ -> return! redirectTo false "/voyages" next ctx
            | Error err ->
                ctx.SetStatusCode 400
                let action = sprintf "/voyages/%s/edit" voyageNumberStr

                return!
                    htmlView
                        (Views.voyageForm (rolesOf ctx) "航海スケジュール更新" action true values (Some(domainErrorMessage err)))
                        next
                        ctx
        }

// ---- US07/US08: 経路設計依頼・候補算出（ROLE_ROUTE_DESIGNER）----

/// 貨物種別文字列を Routing の CargoTypeTag へ変換する（既定は General）。
let private toCargoTypeTag (s: string) : CargoTracker.Routing.Domain.CargoTypeTag =
    match CargoTracker.Routing.Domain.CargoTypeTag.ofString s with
    | Ok tag -> tag
    | Error _ -> CargoTracker.Routing.Domain.General

/// 経路設計依頼一覧（`/routing/requests`・US07）。経路設計中（ROUTING_REQUESTED）の予約を表示する。
let private routingRequests: HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()

        let rows =
            CargoTracker.Booking.Infrastructure.CargoQueries.findAll conn
            |> List.filter (fun i -> i.BookingStatus = "ROUTING_REQUESTED")
            |> List.map (fun i ->
                { Views.RoutingRequestRow.BookingId = i.BookingId
                  Views.RoutingRequestRow.CargoType = i.CargoType
                  Views.RoutingRequestRow.Origin = i.Origin
                  Views.RoutingRequestRow.Destination = i.Destination
                  Views.RoutingRequestRow.ArrivalDeadline = i.ArrivalDeadline })

        htmlView (Views.routingRequestList (rolesOf ctx) rows) next ctx

/// 予約の輸送条件から経路候補（Routing 固有型）を算出する（US08）。
/// 選択画面表示（routingDesign）と確定（routingPropose）で同一の候補列を再現するため共通化する。
let private computeCandidatesForBooking
    (conn: System.Data.IDbConnection)
    (deadlineOverride: string option)
    (b: CargoTracker.Booking.Infrastructure.CargoListItem)
    : Async<CargoTracker.Routing.Domain.RouteCandidate list> =
    async {
        let originResult = CargoTracker.Shared.Domain.Location.create b.Origin
        let destResult = CargoTracker.Shared.Domain.Location.create b.Destination

        // US10: 期限調整・再算出。調整値（deadlineOverride）があれば予約の期限より優先する。
        let effectiveDeadline = deadlineOverride |> Option.defaultValue b.ArrivalDeadline

        let deadline =
            match System.DateOnly.TryParse effectiveDeadline with
            | true, d -> System.DateTimeOffset(d.ToDateTime(System.TimeOnly(23, 59, 0)), System.TimeSpan.Zero)
            | _ -> System.DateTimeOffset.MaxValue

        match originResult, destResult with
        | Ok origin, Ok destination ->
            let query: CargoTracker.Routing.Domain.RouteQuery =
                { Origin = origin
                  Destination = destination
                  CargoType = toCargoTypeTag b.CargoType
                  Deadline = deadline }

            let repo =
                CargoTracker.Routing.Infrastructure.VoyageRepository.create conn systemClock

            let! result = CargoTracker.Routing.Application.VoyageWorkflow.computeRoutes repo query

            return
                (match result with
                 | Ok candidates -> candidates
                 | Error _ -> [])
        | _ -> return []
    }

/// 経路設計・候補算出（`/routing/requests/{bookingId}`・US07/US08）。
let private routingDesign (bookingIdStr: string) : HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let booking =
                CargoTracker.Booking.Infrastructure.CargoQueries.findAll conn
                |> List.tryFind (fun i -> i.BookingId = bookingIdStr)

            match booking with
            | None -> return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Some b ->
                // US10: クエリで期限調整値が渡された場合は再算出に使う。
                let deadlineOverride =
                    match ctx.TryGetQueryStringValue "deadline" with
                    | Some d when d <> "" -> Some d
                    | _ -> None

                let! candidates = computeCandidatesForBooking conn deadlineOverride b

                let candidateRows =
                    candidates
                    |> List.mapi (fun i c ->
                        { Views.RouteCandidateRow.Index = i
                          Views.RouteCandidateRow.VoyageNumbers =
                            c.Legs
                            |> List.map (fun l -> CargoTracker.Routing.Domain.VoyageNumber.value l.VoyageNumber)
                            |> String.concat " → "
                          Views.RouteCandidateRow.TransitPorts =
                            c.TransitPorts
                            |> List.map CargoTracker.Shared.Domain.Location.value
                            |> String.concat ", "
                          Views.RouteCandidateRow.TransitDays = c.TransitDays
                          Views.RouteCandidateRow.EstimatedCost = sprintf "¥%s" (c.EstimatedCost.ToString("N0"))
                          Views.RouteCandidateRow.IsDirect = c.IsDirect })

                return!
                    htmlView
                        (Views.routingDesign
                            (rolesOf ctx)
                            bookingIdStr
                            b.Origin
                            b.Destination
                            (deadlineOverride |> Option.defaultValue b.ArrivalDeadline)
                            candidateRows)
                        next
                        ctx
        }

/// 経路候補の選択・確定（`POST /routing/requests/{bookingId}/propose`・US09/US11）。
/// 表示時と同じ候補列を再算出し、選択された候補を Booking の旅程へ変換して予約に紐付ける。
let private routingPropose (bookingIdStr: string) : HttpHandler =
    mustHaveRole "ROLE_ROUTE_DESIGNER"
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let booking =
                CargoTracker.Booking.Infrastructure.CargoQueries.findAll conn
                |> List.tryFind (fun i -> i.BookingId = bookingIdStr)

            match booking with
            | None -> return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
            | Some b ->
                let! form = ctx.Request.ReadFormAsync()

                // US10: 調整期限で算出した候補を確定する場合、同じ期限で再算出して整合させる。
                let deadlineOverride =
                    match string form.["deadline"] with
                    | "" -> None
                    | d -> Some d

                let! candidates = computeCandidatesForBooking conn deadlineOverride b

                let selectedIndex =
                    match System.Int32.TryParse(string form.["candidateIndex"]) with
                    | true, i -> Some i
                    | _ -> None

                match selectedIndex |> Option.bind (fun i -> List.tryItem i candidates) with
                | None -> return! (setStatusCode 400 >=> text "選択された経路候補が不正です。") next ctx
                | Some candidate ->
                    match CargoTracker.Web.RouteAcl.toCargoItinerary candidate with
                    | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
                    | Ok itinerary ->
                        let repo =
                            CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

                        let dispatcher =
                            CargoTracker.Booking.Infrastructure.StubBookingEventDispatcher.create ()

                        let bookingId = CargoTracker.Booking.Domain.BookingId.ofString bookingIdStr

                        let! result =
                            CargoTracker.Booking.Application.RouteAssignment.proposeRoute
                                repo
                                dispatcher
                                bookingId
                                itinerary

                        match result with
                        | Ok _ -> return! redirectTo false (sprintf "/bookings/%s?msg=routed" bookingIdStr) next ctx
                        | Error(CargoTracker.Shared.Domain.NotFound _) ->
                            return! (setStatusCode 404 >=> text "予約が見つかりません。") next ctx
                        | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

// ---- US18: 貨物追跡照会（ROLE_SHIPPER/CONSIGNEE/TRACKER + 未認証公開）----

/// TrackingView を表示用 DTO に変換する。現在地は最新イベント（末尾）の場所、
/// 推定到着日は合成層で解決した予約の到着予定日を受け取る（レビュー高#5）。
let private toTrackingDetailView
    (estimatedArrival: string)
    (view: CargoTracker.Tracking.Infrastructure.TrackingView)
    : Views.TrackingDetailView =
    { TrackingNumber = view.TrackingNumber
      TransportStatus = view.TransportStatus
      CurrentLocation =
        view.Events
        |> List.tryLast
        |> Option.map (fun e -> e.Location)
        |> Option.defaultValue ""
      EstimatedArrival = estimatedArrival
      Events =
        view.Events
        |> List.map (fun e ->
            { Views.TrackingEventRow.EventType = e.EventType
              Views.TrackingEventRow.Location = e.Location
              Views.TrackingEventRow.EventTime = e.EventTime })
      Exceptions =
        view.Exceptions
        |> List.map (fun x ->
            { Views.TrackingExceptionRow.Index = x.Index
              Views.TrackingExceptionRow.ExceptionType = x.ExceptionType
              Views.TrackingExceptionRow.Location = x.Location
              Views.TrackingExceptionRow.OccurredAt = x.OccurredAt
              Views.TrackingExceptionRow.Description = x.Description
              Views.TrackingExceptionRow.Escalated = x.Escalated
              Views.TrackingExceptionRow.Resolved = x.Resolved }) }

/// 追跡番号入力（`GET /tracking`・US18）。
let private trackingInput: HttpHandler =
    mustHaveAnyRole [ "ROLE_SHIPPER"; "ROLE_CONSIGNEE"; "ROLE_TRACKER" ]
    >=> fun next ctx -> htmlView (Views.trackingInput (rolesOf ctx) None) next ctx

/// 追跡番号での照会（`GET /tracking/search?trackingNumber=`・US18）。PRG 的に詳細へ。
let private trackingSearch: HttpHandler =
    mustHaveAnyRole [ "ROLE_SHIPPER"; "ROLE_CONSIGNEE"; "ROLE_TRACKER" ]
    >=> fun next ctx ->
        task {
            match ctx.TryGetQueryStringValue "trackingNumber" with
            | Some tn when tn <> "" -> return! redirectTo false (sprintf "/tracking/%s" tn) next ctx
            | _ -> return! htmlView (Views.trackingInput (rolesOf ctx) (Some "追跡番号を入力してください。")) next ctx
        }

/// 追跡詳細（`GET /tracking/{trackingNumber}`・US18・認証あり）。
let private trackingDetail (trackingNumber: string) : HttpHandler =
    mustHaveAnyRole [ "ROLE_SHIPPER"; "ROLE_CONSIGNEE"; "ROLE_TRACKER" ]
    >=> fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            match CargoTracker.Tracking.Infrastructure.TrackingQueries.findByTrackingNumber conn trackingNumber with
            | Some view ->
                // 推定到着日は合成層で Booking（cargo.arrival_deadline）から解決する（BC 分離・レビュー高#5）。
                let eta =
                    CargoTracker.Booking.Infrastructure.CargoQueries.findArrivalDeadline conn view.BookingId
                    |> Option.defaultValue ""

                return! htmlView (Views.trackingDetail (rolesOf ctx) (toTrackingDetailView eta view)) next ctx
            | None ->
                return!
                    (setStatusCode 404
                     >=> htmlView (Views.trackingInput (rolesOf ctx) (Some "追跡番号が見つかりません。")))
                        next
                        ctx
        }

/// 貨物状態手動更新フォーム（`GET /tracking/{trackingNumber}/status/new`・US17・ROLE_TRACKER）。
let private manualStatusNew (trackingNumber: string) : HttpHandler =
    mustHaveRole "ROLE_TRACKER"
    >=> fun next ctx -> htmlView (Views.manualStatusForm (rolesOf ctx) trackingNumber None) next ctx

/// 貨物状態手動更新の実行（`POST /tracking/{trackingNumber}/status`・US17）。
let private manualStatusUpdate (trackingNumber: string) : HttpHandler =
    mustHaveRole "ROLE_TRACKER"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get (k: string) = string form.[k]
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let eventTypeResult =
                CargoTracker.Tracking.Domain.TrackingEventType.ofString (get "eventType")

            let locationResult =
                CargoTracker.Shared.Domain.Location.create (get "location")
                |> Result.mapError (fun m -> CargoTracker.Shared.Domain.ValidationError("Location", m))

            match eventTypeResult, locationResult with
            | Ok eventType, Ok location ->
                let repo =
                    CargoTracker.Tracking.Infrastructure.TrackingRepository.create conn systemClock

                let notifier: CargoTracker.Tracking.Application.TrackingNotifier =
                    { Notify = fun _ _ -> async { return Ok() } }

                let event: CargoTracker.Tracking.Domain.TrackingActivityEvent =
                    { EventType = eventType
                      Location = location
                      CompletionTime = systemClock () }

                let! result =
                    CargoTracker.Tracking.Application.RecordTracking.record
                        repo
                        notifier
                        (CargoTracker.Tracking.Domain.TrackingNumber.ofString trackingNumber)
                        event

                match result with
                | Ok _ -> return! redirectTo false (sprintf "/tracking/%s" trackingNumber) next ctx
                | Error(CargoTracker.Shared.Domain.NotFound _) ->
                    return! (setStatusCode 404 >=> text "追跡番号が見つかりません。") next ctx
                | Error err ->
                    return!
                        (setStatusCode 400
                         >=> htmlView (
                             Views.manualStatusForm (rolesOf ctx) trackingNumber (Some(domainErrorMessage err))
                         ))
                            next
                            ctx
            | _ ->
                return!
                    (setStatusCode 400
                     >=> htmlView (Views.manualStatusForm (rolesOf ctx) trackingNumber (Some "入力が不正です。")))
                        next
                        ctx
        }

// ---- US19/US20: 例外登録・解決（ROLE_TRACKER）----

/// notification_log へ書き込む TrackingNotifier（荷主通知の最小実装）。
/// recipient の荷主識別子化は IT6 task4.6（通知モデル化）で是正する。
let private notificationLogNotifier
    (conn: System.Data.IDbConnection)
    : CargoTracker.Tracking.Application.TrackingNotifier =
    { Notify =
        fun trackingNumber message ->
            async {
                try
                    let now = (systemClock ()).UtcDateTime.ToString("o")
                    let tn = CargoTracker.Tracking.Domain.TrackingNumber.value trackingNumber

                    conn
                    |> Donald.Db.newCommand
                        "INSERT INTO notification_log (booking_id, recipient, message, notified_at, created_at) VALUES (@bid, @rcp, @msg, @now, @now)"
                    |> Donald.Db.setParams
                        [ "bid", Donald.SqlType.String tn
                          "rcp", Donald.SqlType.String tn
                          "msg", Donald.SqlType.String message
                          "now", Donald.SqlType.String now ]
                    |> Donald.Db.exec

                    return Ok()
                with ex ->
                    return Error(CargoTracker.Shared.Domain.BusinessRuleViolation("TrackingNotifier", ex.Message))
            } }

/// 管理職エスカレーション通知（notification_log に ESCALATION として記録・US20 紛失時）。
let private escalationLogNotifier
    (conn: System.Data.IDbConnection)
    : CargoTracker.Tracking.Application.EscalationNotifier =
    { Escalate =
        fun trackingNumber exType ->
            async {
                try
                    let now = (systemClock ()).UtcDateTime.ToString("o")
                    let tn = CargoTracker.Tracking.Domain.TrackingNumber.value trackingNumber

                    let message =
                        sprintf
                            "【緊急】追跡番号 %s の例外（%s）を管理職へエスカレーションしました。"
                            tn
                            (CargoTracker.Tracking.Domain.ExceptionType.toString exType)

                    conn
                    |> Donald.Db.newCommand
                        "INSERT INTO notification_log (booking_id, recipient, message, notified_at, created_at) VALUES (@bid, @rcp, @msg, @now, @now)"
                    |> Donald.Db.setParams
                        [ "bid", Donald.SqlType.String tn
                          "rcp", Donald.SqlType.String "MANAGER"
                          "msg", Donald.SqlType.String message
                          "now", Donald.SqlType.String now ]
                    |> Donald.Db.exec

                    return Ok()
                with ex ->
                    return Error(CargoTracker.Shared.Domain.BusinessRuleViolation("EscalationNotifier", ex.Message))
            } }

/// 例外登録フォーム（`GET /tracking/{trackingNumber}/exceptions/new`・US19/US20・ROLE_TRACKER）。
let private exceptionNew (trackingNumber: string) : HttpHandler =
    mustHaveRole "ROLE_TRACKER"
    >=> fun next ctx -> htmlView (Views.exceptionForm (rolesOf ctx) trackingNumber None) next ctx

/// 例外登録の実行（`POST /tracking/{trackingNumber}/exceptions/new`・US19/US20）。
let private exceptionCreate (trackingNumber: string) : HttpHandler =
    mustHaveRole "ROLE_TRACKER"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get (k: string) = string form.[k]
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let exTypeResult =
                CargoTracker.Tracking.Domain.ExceptionType.ofString (get "exceptionType")

            let locationResult =
                CargoTracker.Shared.Domain.Location.create (get "location")
                |> Result.mapError (fun m -> CargoTracker.Shared.Domain.ValidationError("Location", m))

            match exTypeResult, locationResult with
            | Ok exType, Ok location ->
                let repo =
                    CargoTracker.Tracking.Infrastructure.TrackingRepository.create conn systemClock

                let! result =
                    CargoTracker.Tracking.Application.ManageException.register
                        repo
                        (notificationLogNotifier conn)
                        (escalationLogNotifier conn)
                        (CargoTracker.Tracking.Domain.TrackingNumber.ofString trackingNumber)
                        exType
                        location
                        (systemClock ())
                        (get "description")

                match result with
                | Ok _ -> return! redirectTo false (sprintf "/tracking/%s" trackingNumber) next ctx
                | Error(CargoTracker.Shared.Domain.NotFound _) ->
                    return! (setStatusCode 404 >=> text "追跡番号が見つかりません。") next ctx
                | Error err ->
                    return!
                        (setStatusCode 400
                         >=> htmlView (Views.exceptionForm (rolesOf ctx) trackingNumber (Some(domainErrorMessage err))))
                            next
                            ctx
            | _ ->
                return!
                    (setStatusCode 400
                     >=> htmlView (Views.exceptionForm (rolesOf ctx) trackingNumber (Some "入力が不正です。")))
                        next
                        ctx
        }

/// 例外解決の実行（`POST /tracking/{trackingNumber}/exceptions/{index}/resolve`・US19/US20）。
let private exceptionResolve (trackingNumber: string, index: int) : HttpHandler =
    mustHaveRole "ROLE_TRACKER"
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Tracking.Infrastructure.TrackingRepository.create conn systemClock

            let! result =
                CargoTracker.Tracking.Application.ManageException.resolve
                    repo
                    (notificationLogNotifier conn)
                    (CargoTracker.Tracking.Domain.TrackingNumber.ofString trackingNumber)
                    index
                    (systemClock ())
                    (string form.["resolutionNote"])

            match result with
            | Ok _ -> return! redirectTo false (sprintf "/tracking/%s" trackingNumber) next ctx
            | Error(CargoTracker.Shared.Domain.NotFound _) ->
                return! (setStatusCode 404 >=> text "例外が見つかりません。") next ctx
            | Error err -> return! (setStatusCode 400 >=> text (domainErrorMessage err)) next ctx
        }

/// 公開追跡（`GET /public/tracking/{accessToken}`・US18・未認証）。
let private publicTracking (accessToken: string) : HttpHandler =
    fun next ctx ->
        task {
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            match CargoTracker.Tracking.Infrastructure.TrackingQueries.findByAccessToken conn accessToken with
            | Some view ->
                let eta =
                    CargoTracker.Booking.Infrastructure.CargoQueries.findArrivalDeadline conn view.BookingId
                    |> Option.defaultValue ""

                return! htmlView (Views.publicTracking (toTrackingDetailView eta view)) next ctx
            | None -> return! (setStatusCode 404 >=> text "追跡番号が見つかりません。") next ctx
        }

// ---- US15/US16: 荷役作業登録（ROLE_HANDLER/TRACKER）----

/// 荷役種別を HandlingType へ変換する（積込/荷降しは航海番号必須）。
let private parseHandlingType
    (handlingType: string)
    (voyageNumber: string)
    : Result<CargoTracker.Handling.Domain.HandlingType, CargoTracker.Shared.Domain.DomainError> =
    match handlingType with
    | "RECEIVE" -> Ok CargoTracker.Handling.Domain.Receive
    | "CLAIM" -> Ok CargoTracker.Handling.Domain.Claim
    | "LOAD" ->
        CargoTracker.Handling.Domain.VoyageNumber.create voyageNumber
        |> Result.map CargoTracker.Handling.Domain.Load
    | "UNLOAD" ->
        CargoTracker.Handling.Domain.VoyageNumber.create voyageNumber
        |> Result.map CargoTracker.Handling.Domain.Unload
    | other -> Error(CargoTracker.Shared.Domain.ValidationError("HandlingType", sprintf "未知の作業種別です: %s" other))

/// 荷役種別を Tracking のイベント種別へ写像する（Customs は追跡イベント無し）。
let private toTrackingEventType
    (handlingType: CargoTracker.Handling.Domain.HandlingType)
    : CargoTracker.Tracking.Domain.TrackingEventType option =
    match handlingType with
    | CargoTracker.Handling.Domain.Receive -> Some CargoTracker.Tracking.Domain.ReceivedEvent
    | CargoTracker.Handling.Domain.Load _ -> Some CargoTracker.Tracking.Domain.LoadedEvent
    | CargoTracker.Handling.Domain.Unload _ -> Some CargoTracker.Tracking.Domain.UnloadedEvent
    | CargoTracker.Handling.Domain.Claim -> Some CargoTracker.Tracking.Domain.ClaimedEvent
    | CargoTracker.Handling.Domain.Customs -> None

/// 荷役作業一覧（`GET /handling`・US15）。登録直後の妥当性結果を banner で表示する（レビュー）。
let private handlingList: HttpHandler =
    mustHaveAnyRole [ "ROLE_HANDLER"; "ROLE_TRACKER" ]
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()

        // 荷役登録の PRG 後フィードバック（Misrouted/Warning/成功）は Views 側で banner 化する。
        let msg = ctx.TryGetQueryStringValue "msg"

        let rows =
            CargoTracker.Handling.Infrastructure.HandlingQueries.findAll conn
            |> List.map (fun r ->
                { Views.HandlingRow.BookingId = r.BookingId
                  Views.HandlingRow.HandlingType = r.HandlingType
                  Views.HandlingRow.Location = r.Location
                  Views.HandlingRow.CompletionTime = r.CompletionTime
                  Views.HandlingRow.VoyageNumber = r.VoyageNumber |> Option.defaultValue "-" })

        htmlView (Views.handlingList (rolesOf ctx) msg rows) next ctx

/// 荷役登録フォーム（`GET /handling/new`・US15/US16）。
let private handlingNew: HttpHandler =
    mustHaveAnyRole [ "ROLE_HANDLER"; "ROLE_TRACKER" ]
    >=> fun next ctx -> htmlView (Views.handlingForm (rolesOf ctx) None None) next ctx

/// 荷役登録の実行（`POST /handling`・US15/US16）。
/// 荷役登録成功後、対応する追跡イベントを記録して追跡状態を更新する（BC 連携・US15 受入4）。
let private handlingCreate: HttpHandler =
    mustHaveAnyRole [ "ROLE_HANDLER"; "ROLE_TRACKER" ]
    >=> fun next ctx ->
        task {
            let! form = ctx.Request.ReadFormAsync()
            let get (k: string) = string form.[k]
            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let trackingNumberStr = get "trackingNumber"

            let consignee =
                match get "consigneeConfirmation" with
                | "" -> None
                | c -> Some c

            let completionTime = systemClock ()

            let locationResult =
                CargoTracker.Shared.Domain.Location.create (get "location")
                |> Result.mapError (fun m -> CargoTracker.Shared.Domain.ValidationError("Location", m))

            match parseHandlingType (get "handlingType") (get "voyageNumber"), locationResult with
            | Ok handlingType, Ok location ->
                let repo =
                    CargoTracker.Handling.Infrastructure.HandlingRepository.create conn systemClock

                let! result =
                    CargoTracker.Handling.Application.RegisterHandling.register
                        repo
                        (CargoTracker.Web.HandlingAcl.cargoSnapshotProvider conn)
                        trackingNumberStr
                        handlingType
                        location
                        completionTime
                        consignee

                match result with
                | Ok(_, outcome, _) ->
                    // BC 連携: 荷役に対応する追跡イベントを記録して状態を進める（US15 受入4）。
                    let trackingRepo =
                        CargoTracker.Tracking.Infrastructure.TrackingRepository.create conn systemClock

                    let notifier: CargoTracker.Tracking.Application.TrackingNotifier =
                        { Notify = fun _ _ -> async { return Ok() } }

                    match toTrackingEventType handlingType with
                    | Some eventType ->
                        let event: CargoTracker.Tracking.Domain.TrackingActivityEvent =
                            { EventType = eventType
                              Location = location
                              CompletionTime = completionTime }

                        // 荷役は保存済み（別トランザクション）。追跡記録はベストエフォートで、失敗は握り潰さずログする
                        // （xp-programmer 高#1・完全な原子性は将来 UoW 化で対応）。
                        let! trackingResult =
                            CargoTracker.Tracking.Application.RecordTracking.record
                                trackingRepo
                                notifier
                                (CargoTracker.Tracking.Domain.TrackingNumber.ofString trackingNumberStr)
                                event

                        match trackingResult with
                        | Ok _ -> ()
                        | Error e -> eprintfn "[handlingCreate] 追跡状態の更新に失敗（荷役は登録済み）: %A" e
                    | None -> ()

                    let msg =
                        match outcome with
                        | CargoTracker.Handling.Domain.Misrouted -> "handling_misrouted"
                        | CargoTracker.Handling.Domain.Warning _ -> "handling_warning"
                        | CargoTracker.Handling.Domain.Valid -> "handling_ok"

                    return! redirectTo false (sprintf "/handling?msg=%s" msg) next ctx
                | Error(CargoTracker.Shared.Domain.NotFound _) ->
                    return!
                        (setStatusCode 404
                         >=> htmlView (Views.handlingForm (rolesOf ctx) None (Some "追跡番号が見つかりません。")))
                            next
                            ctx
                | Error err ->
                    return!
                        (setStatusCode 400
                         >=> htmlView (Views.handlingForm (rolesOf ctx) None (Some(domainErrorMessage err))))
                            next
                            ctx
            | Error err, _
            | _, Error err ->
                let msg =
                    match err with
                    | CargoTracker.Shared.Domain.ValidationError(_, m) -> m
                    | _ -> "入力が不正です。"

                return!
                    (setStatusCode 400
                     >=> htmlView (Views.handlingForm (rolesOf ctx) None (Some msg)))
                        next
                        ctx
        }

/// ルーティング定義。公開パス（/health・/login）以外は認証を要求する。
let webApp: HttpHandler =
    choose
        [ GET
          >=> choose
                  [ route "/health" >=> text "Healthy"
                    route "/login"
                    >=> htmlView (Views.login Seed.DefaultUsername Seed.DefaultPassword Seed.defaultUsernames None)
                    route "/" >=> dashboard
                    route "/shippers" >=> shipperList
                    route "/shippers/new" >=> shipperNew
                    route "/estimates" >=> estimateList
                    route "/estimates/new" >=> estimateNew
                    route "/bookings" >=> bookingList
                    // 具体パス `/bookings/new` を `routef "/bookings/%s"` より先に置く（順序依存・"new" が %s に食われないように）。
                    route "/bookings/new" >=> bookingNew
                    routef "/bookings/%s" bookingDetail
                    // US18: 貨物追跡照会（具体パスを routef より先に置く）。
                    route "/tracking" >=> trackingInput
                    route "/tracking/search" >=> trackingSearch
                    routef "/public/tracking/%s" publicTracking
                    routef "/tracking/%s/status/new" manualStatusNew
                    routef "/tracking/%s/exceptions/new" exceptionNew
                    routef "/tracking/%s" trackingDetail
                    // US15/US16: 荷役作業（具体パス /handling/new を先に置く）。
                    route "/handling/new" >=> handlingNew
                    route "/handling" >=> handlingList
                    route "/voyages" >=> voyageList
                    route "/voyages/new" >=> voyageNew
                    routef "/voyages/%s/edit" voyageEdit
                    route "/routing/requests" >=> routingRequests
                    routef "/routing/requests/%s" routingDesign
                    route "/admin/discount-policies" >=> placeholder "割引ポリシー管理" [ "ROLE_ADMIN" ] ]
          POST
          >=> choose
                  [ route "/login" >=> loginPost
                    route "/logout" >=> logout
                    route "/shippers" >=> shipperCreate
                    route "/estimates" >=> estimateCreate
                    route "/bookings" >=> bookingCreate
                    route "/handling" >=> handlingCreate
                    routef "/tracking/%s/exceptions/%i/resolve" exceptionResolve
                    routef "/tracking/%s/exceptions/new" exceptionCreate
                    routef "/tracking/%s/status" manualStatusUpdate
                    routef "/bookings/%s/routing" bookingSubmitRouting
                    routef "/bookings/%s/confirm" bookingConfirm
                    routef "/bookings/%s/restore" bookingRestore
                    routef "/bookings/%s/cancel" bookingCancel
                    routef "/bookings/%s/notify" bookingNotify
                    route "/voyages" >=> voyageCreate
                    routef "/voyages/%s/edit" voyageUpdate
                    routef "/voyages/%s/confirm" voyageConfirm
                    routef "/routing/requests/%s/propose" routingPropose ]
          setStatusCode 404 >=> text "Not Found" ]

/// DI 構成。Giraffe + Cookie 認証 + 接続ファクトリを登録する。
let configureServices (config: IConfiguration) (services: IServiceCollection) : unit =
    services.AddGiraffe() |> ignore

    services
        .AddAuthentication(CookieAuthenticationDefaults.AuthenticationScheme)
        .AddCookie(fun opts ->
            opts.LoginPath <- PathString "/login"
            opts.LogoutPath <- PathString "/logout"
            opts.AccessDeniedPath <- PathString "/login")
    |> ignore

    let provider = config.["Database:Provider"] |> Db.providerOfString

    let connStr =
        match config.["Database:ConnectionString"] with
        | null
        | "" -> "Data Source=cargo_tracker.db"
        | s -> s

    let factory: ConnectionFactory = fun () -> Db.openConnection provider connStr
    services.AddSingleton<ConnectionFactory>(factory) |> ignore

/// パイプライン構成。認証 → Giraffe の順で組む。
let configureApp (app: IApplicationBuilder) : unit =
    app.UseAuthentication() |> ignore
    app.UseGiraffe webApp
