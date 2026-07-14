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

/// ルーティング定義。公開パス（/health・/login）以外は認証を要求する。
let webApp: HttpHandler =
    choose
        [ GET
          >=> choose
                  [ route "/health" >=> text "Healthy"
                    route "/login" >=> htmlView (Views.login "" None)
                    route "/" >=> dashboard ]
          POST >=> choose [ route "/login" >=> loginPost; route "/logout" >=> logout ]
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
