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
      CanSubmitRouting = (cargo.State = CargoTracker.Booking.Domain.Preliminary) }

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

            match found with
            | Ok(Some cargo) ->
                return! htmlView (Views.bookingDetail (rolesOf ctx) (toBookingDetail shipperNames cargo) None) next ctx
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
                // 予約の輸送条件から経路探索クエリを構成する。
                let originResult = CargoTracker.Shared.Domain.Location.create b.Origin
                let destResult = CargoTracker.Shared.Domain.Location.create b.Destination

                let deadline =
                    match System.DateOnly.TryParse b.ArrivalDeadline with
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

                    let candidateRows =
                        match result with
                        | Ok candidates ->
                            candidates
                            |> List.map (fun c ->
                                { Views.RouteCandidateRow.VoyageNumbers =
                                    c.Legs
                                    |> List.map (fun l ->
                                        CargoTracker.Routing.Domain.VoyageNumber.value l.VoyageNumber)
                                    |> String.concat " → "
                                  Views.RouteCandidateRow.TransitPorts =
                                    c.TransitPorts
                                    |> List.map CargoTracker.Shared.Domain.Location.value
                                    |> String.concat ", "
                                  Views.RouteCandidateRow.TransitDays = c.TransitDays
                                  Views.RouteCandidateRow.EstimatedCost =
                                    sprintf "¥%s" (c.EstimatedCost.ToString("N0"))
                                  Views.RouteCandidateRow.IsDirect = c.IsDirect })
                        | Error _ -> []

                    return!
                        htmlView
                            (Views.routingDesign
                                (rolesOf ctx)
                                bookingIdStr
                                b.Origin
                                b.Destination
                                b.ArrivalDeadline
                                candidateRows)
                            next
                            ctx
                | _ -> return! (setStatusCode 400 >=> text "予約の出発地・目的地が不正です。") next ctx
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
                    // ウォーキングスケルトン: 後続 IT で実画面化するプレースホルダ（ADR-0005 ロール制御）
                    route "/tracking"
                    >=> placeholder "貨物追跡" [ "ROLE_SHIPPER"; "ROLE_CONSIGNEE"; "ROLE_TRACKER" ]
                    route "/handling" >=> placeholder "荷役管理" [ "ROLE_HANDLER"; "ROLE_TRACKER" ]
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
                    routef "/bookings/%s/routing" bookingSubmitRouting
                    route "/voyages" >=> voyageCreate
                    routef "/voyages/%s/edit" voyageUpdate
                    routef "/voyages/%s/confirm" voyageConfirm ]
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
