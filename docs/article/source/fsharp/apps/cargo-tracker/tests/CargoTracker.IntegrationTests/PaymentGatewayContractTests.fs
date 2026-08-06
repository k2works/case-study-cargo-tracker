module CargoTracker.IntegrationTests.PaymentGatewayContractTests

open System
open System.Net
open System.Net.Http
open System.Threading
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain
open CargoTracker.Web

// US23・ADR-0014: PaymentGatewayPort の実 HTTP アダプタ契約を固定する。
// 成功・決済拒否（4xx）・障害（5xx）の 3 系統を検証する。
//
// 契約スタブは .NET 標準の HttpListener によるインプロセス HTTP サーバで構成する。
// ADR-0014 は当初 WireMock.Net を想定したが、WireMock.Net が高深刻度の脆弱性を持つ
// 推移的依存（System.Linq.Dynamic.Core・Scriban.Signed 等）を多数引き込むため、
// セキュリティを優先し外部依存ゼロの HttpListener に切り替えた（同等の契約固定を実現）。

let private clock: Clock =
    fun () -> DateTimeOffset(2026, 10, 20, 0, 0, 0, TimeSpan.Zero)

let private invoiceId () = InvoiceId.ofString "INV-CONTRACT01"
let private amount = { Amount = 45_000L; Currency = JPY }

/// 指定の応答（ステータス・本文）を返す HttpListener を起動し、テストへ baseUrl を渡す。
let private withStubServer (statusCode: int) (body: string) (test: string -> unit) =
    // ポート衝突を避けるため OS の割り当てるループバックポートを使う。
    let listener = new HttpListener()
    let port = 18000 + (Environment.ProcessId % 2000)
    let prefix = sprintf "http://127.0.0.1:%d/" port
    listener.Prefixes.Add prefix
    listener.Start()

    let cts = new CancellationTokenSource()

    let loop =
        async {
            while not cts.IsCancellationRequested do
                let! ctx = listener.GetContextAsync() |> Async.AwaitTask
                let buffer = Text.Encoding.UTF8.GetBytes body
                ctx.Response.StatusCode <- statusCode
                ctx.Response.ContentLength64 <- int64 buffer.Length
                ctx.Response.OutputStream.Write(buffer, 0, buffer.Length)
                ctx.Response.OutputStream.Close()
        }

    Async.Start(loop, cts.Token)

    try
        test (prefix.TrimEnd('/'))
    finally
        cts.Cancel()
        listener.Stop()
        listener.Close()

[<Fact>]
[<Trait("Category", "Integration")>]
let ``入金確認が成功すると決済時刻を返す（ADR-0014 契約・成功系）`` () =
    withStubServer 200 "\"2026-10-20T00:00:00+00:00\"" (fun baseUrl ->
        use client = new HttpClient()
        let gateway = PaymentGateway.createHttp client baseUrl clock

        match gateway.ConfirmPayment (invoiceId ()) amount |> Async.RunSynchronously with
        | Ok paidAt -> paidAt.Year |> should equal 2026
        | Error e -> failwithf "成功を期待したが: %A" e)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``決済拒否（402）は PaymentDeclined エラーになる（ADR-0014 契約・拒否系）`` () =
    withStubServer 402 "" (fun baseUrl ->
        use client = new HttpClient()
        let gateway = PaymentGateway.createHttp client baseUrl clock

        match gateway.ConfirmPayment (invoiceId ()) amount |> Async.RunSynchronously with
        | Error(BusinessRuleViolation("PaymentDeclined", _)) -> ()
        | other -> failwithf "PaymentDeclined を期待したが: %A" other)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``決済機関の障害（503）は PaymentGatewayUnavailable エラーになる（ADR-0014 契約・障害系）`` () =
    withStubServer 503 "" (fun baseUrl ->
        use client = new HttpClient()
        let gateway = PaymentGateway.createHttp client baseUrl clock

        match gateway.ConfirmPayment (invoiceId ()) amount |> Async.RunSynchronously with
        | Error(BusinessRuleViolation("PaymentGatewayUnavailable", _)) -> ()
        | other -> failwithf "PaymentGatewayUnavailable を期待したが: %A" other)
