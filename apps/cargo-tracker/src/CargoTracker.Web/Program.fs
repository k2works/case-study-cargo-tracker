module CargoTracker.Web.Program

open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.DependencyInjection
open Giraffe

/// ルーティング定義。ヘルスチェックは Giraffe の route で最小構成とする。
let webApp: HttpHandler = choose [ route "/health" >=> text "Healthy" ]

[<EntryPoint>]
let main args =
    let builder = WebApplication.CreateBuilder(args)
    builder.Services.AddGiraffe() |> ignore
    let app = builder.Build()
    app.UseGiraffe webApp
    app.Run("http://0.0.0.0:8080")
    0
