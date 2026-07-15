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

/// 貨物予約一覧（`/bookings`）。IT1 ウォーキングスケルトンのプレースホルダを実画面へ差し替え。
let private bookingList: HttpHandler =
    mustHaveAnyRole [ "ROLE_SALES"; "ROLE_SHIPPER" ]
    >=> fun next ctx ->
        let factory = ctx.GetService<ConnectionFactory>()
        use conn = factory ()
        let items = CargoTracker.Booking.Infrastructure.CargoQueries.findAll conn

        let rows =
            items
            |> List.map (fun i ->
                { Views.CargoRow.BookingId = i.BookingId
                  Views.CargoRow.ShipperId = i.ShipperId
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

            let deadline =
                match System.DateOnly.TryParse(get "arrivalDeadline") with
                | true, d -> d
                | _ -> System.DateOnly.FromDateTime(System.DateTime.Today)

            let weight =
                match System.Decimal.TryParse(get "weightKg") with
                | true, w -> w
                | _ -> -1m // 範囲外にしてドメイン検証で弾く

            let cmd: CargoTracker.Booking.Application.BookCargoCommand =
                { ShipperId = get "shipperId"
                  OriginUnlocode = get "originUnlocode"
                  DestinationUnlocode = get "destinationUnlocode"
                  ArrivalDeadline = deadline
                  CargoType = buildCargoTypeInput get
                  WeightKg = weight
                  Consignee = None }

            let factory = ctx.GetService<ConnectionFactory>()
            use conn = factory ()

            let repo =
                CargoTracker.Booking.Infrastructure.CargoRepository.create conn systemClock

            let shipperChecker =
                CargoTracker.Booking.Infrastructure.ShipperExistenceAdapter.create conn

            let newId: CargoTracker.Shared.Domain.IdGenerator = fun () -> System.Guid.NewGuid()
            let! result = CargoTracker.Booking.Application.BookCargo.book repo shipperChecker newId cmd

            match result with
            | Ok _ -> return! redirectTo false "/bookings" next ctx
            | Error err ->
                ctx.SetStatusCode 400
                let shippers = loadShipperChoices conn

                return!
                    htmlView (Views.bookingForm (rolesOf ctx) shippers values (Some(domainErrorMessage err))) next ctx
        }

// ---- US06: 予約詳細・経路設計依頼 ----

/// Cargo 集約を詳細表示用の DTO へ射影する。
let private toBookingDetail (cargo: CargoTracker.Booking.Domain.Cargo) : Views.BookingDetail =
    let cargoTypeStr =
        match cargo.CargoType with
        | CargoTracker.Booking.Domain.General -> "GENERAL"
        | CargoTracker.Booking.Domain.Hazardous _ -> "HAZARDOUS"
        | CargoTracker.Booking.Domain.Refrigerated _ -> "REFRIGERATED"

    let spec = cargo.RouteSpecification

    { BookingId = CargoTracker.Booking.Domain.BookingId.value cargo.BookingId
      ShipperId = (CargoTracker.Shared.Domain.ShipperId.value cargo.ShipperId).ToString("D")
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

            match found with
            | Ok(Some cargo) ->
                return! htmlView (Views.bookingDetail (rolesOf ctx) (toBookingDetail cargo) None) next ctx
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
                    route "/bookings/new" >=> bookingNew
                    routef "/bookings/%s" bookingDetail
                    // ウォーキングスケルトン: 後続 IT で実画面化するプレースホルダ（ADR-0005 ロール制御）
                    route "/tracking"
                    >=> placeholder "貨物追跡" [ "ROLE_SHIPPER"; "ROLE_CONSIGNEE"; "ROLE_TRACKER" ]
                    route "/handling" >=> placeholder "荷役管理" [ "ROLE_HANDLER"; "ROLE_TRACKER" ]
                    route "/voyages" >=> placeholder "航路管理" [ "ROLE_ROUTE_DESIGNER" ]
                    route "/admin/discount-policies" >=> placeholder "割引ポリシー管理" [ "ROLE_ADMIN" ] ]
          POST
          >=> choose
                  [ route "/login" >=> loginPost
                    route "/logout" >=> logout
                    route "/shippers" >=> shipperCreate
                    route "/estimates" >=> estimateCreate
                    route "/bookings" >=> bookingCreate
                    routef "/bookings/%s/routing" bookingSubmitRouting ]
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
