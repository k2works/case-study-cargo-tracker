namespace CargoTracker.Web

open System
open System.Net.Http
open System.Text
open System.Threading.Tasks
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain
open CargoTracker.Billing.Application

/// 決済機関との連携 ACL（US23・ADR-0014）。`PaymentGatewayPort` の実 HTTP アダプタ。
/// 外部決済 API へ入金確認要求を送り、契約（成功・決済拒否・タイムアウト）を Result へ写像する。
/// 契約は WireMock.Net で固定してテストする（BC 外部との腐敗防止層）。
module PaymentGateway =

    /// スタブ（合成層・入金確認を即時成功させる）。実基盤未整備時の既定（IT7 互換）。
    let stub (clock: Clock) : PaymentGatewayPort =
        { ConfirmPayment = fun _ _ -> async { return Ok(clock ()) } }

    /// 実 HTTP アダプタ（ADR-0014）。`baseUrl` の決済 API へ `POST /payments/confirm` する。
    /// - 200 OK: 本文（ISO8601 の入金時刻）を DateTimeOffset として返す（無ければ現在時刻）。
    /// - 402 Payment Required 等の 4xx: 決済拒否として BusinessRuleViolation。
    /// - タイムアウト・接続失敗・5xx: 一時障害として BusinessRuleViolation。
    let createHttp (client: HttpClient) (baseUrl: string) (clock: Clock) : PaymentGatewayPort =
        { ConfirmPayment =
            fun invoiceId amount ->
                async {
                    let payload =
                        sprintf
                            """{"invoiceId":"%s","amount":%d,"currency":"%s"}"""
                            (InvoiceId.value invoiceId)
                            amount.Amount
                            (CurrencyCode.toString amount.Currency)

                    let url = sprintf "%s/payments/confirm" (baseUrl.TrimEnd('/'))

                    try
                        use content = new StringContent(payload, Encoding.UTF8, "application/json")
                        let! resp = client.PostAsync(url, content) |> Async.AwaitTask

                        if resp.IsSuccessStatusCode then
                            let! body = resp.Content.ReadAsStringAsync() |> Async.AwaitTask

                            match DateTimeOffset.TryParse(body.Trim('"', ' ', '\n', '\r')) with
                            | true, paidAt -> return Ok paidAt
                            | _ -> return Ok(clock ())
                        elif int resp.StatusCode >= 400 && int resp.StatusCode < 500 then
                            return
                                Error(
                                    BusinessRuleViolation(
                                        "PaymentDeclined",
                                        sprintf "決済が拒否されました（HTTP %d）。" (int resp.StatusCode)
                                    )
                                )
                        else
                            return
                                Error(
                                    BusinessRuleViolation(
                                        "PaymentGatewayUnavailable",
                                        sprintf "決済機関が応答しませんでした（HTTP %d）。" (int resp.StatusCode)
                                    )
                                )
                    with
                    | :? TaskCanceledException ->
                        return Error(BusinessRuleViolation("PaymentGatewayTimeout", "決済機関への要求がタイムアウトしました。"))
                    | ex -> return Error(BusinessRuleViolation("PaymentGatewayUnavailable", ex.Message))
                } }
