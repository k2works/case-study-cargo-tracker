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

                return! htmlView (Views.login username (Some "ユーザー ID またはパスワードが正しくありません。")) next ctx
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
                { Views.EstimateId = i.EstimateId
                  Views.Origin = i.Origin
                  Views.Destination = i.Destination
                  Views.ArrivalDeadline = i.ArrivalDeadline
                  Views.CargoType = i.CargoType
                  Views.WeightKg = i.WeightKg
                  Views.Status = i.Status
                  Views.CandidateCount = i.CandidateCount })

        htmlView (Views.estimateList (rolesOf ctx) rows) next ctx

let private estimateNew: HttpHandler =
    mustHaveRole "ROLE_SALES"
    >=> fun next ctx -> htmlView (Views.estimateForm (rolesOf ctx) Views.emptyEstimateForm None) next ctx

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
                    route "/login" >=> htmlView (Views.login "" None)
                    route "/" >=> dashboard
                    route "/shippers" >=> shipperList
                    route "/shippers/new" >=> shipperNew
                    route "/estimates" >=> estimateList
                    route "/estimates/new" >=> estimateNew ]
          POST
          >=> choose
                  [ route "/login" >=> loginPost
                    route "/logout" >=> logout
                    route "/shippers" >=> shipperCreate
                    route "/estimates" >=> estimateCreate ]
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
