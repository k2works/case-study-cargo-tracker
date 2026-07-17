module CargoTracker.IntegrationTests.InvoiceRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain
open CargoTracker.Billing.Application
open CargoTracker.Billing.Infrastructure

// US21-US23: Invoice の永続化（発行・入金確認・往復）と精算ユースケース。

let private ddl =
    """
    CREATE TABLE invoice (
        id                    INTEGER PRIMARY KEY AUTOINCREMENT,
        invoice_number        TEXT    NOT NULL UNIQUE,
        booking_id            TEXT    NOT NULL UNIQUE,
        shipper_id            TEXT    NOT NULL,
        base_amount_value     INTEGER NOT NULL,
        base_amount_currency  TEXT    NOT NULL,
        discount_rate         NUMERIC NOT NULL,
        final_amount_value    INTEGER NOT NULL,
        final_amount_currency TEXT    NOT NULL,
        payment_status        TEXT    NOT NULL,
        issued_at             TEXT    NOT NULL,
        due_date              TEXT,
        paid_at               TEXT,
        created_at            TEXT    NOT NULL,
        updated_at            TEXT    NOT NULL
    );
    """

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- ddl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: Clock =
    fun () -> DateTimeOffset(2026, 10, 6, 0, 0, 0, TimeSpan.Zero)

let private newId () : Guid = Guid.NewGuid()

let private bookingId () =
    match BillingBookingId.create "BKG-0001" with
    | Ok b -> b
    | Error e -> failwithf "%A" e

let private corporate () =
    match BillingShipperId.create "SHP-CORP" true with
    | Ok s -> s
    | Error e -> failwithf "%A" e

let private notifier (calls: System.Collections.Generic.List<string>) : BillingNotifier =
    { Notify =
        fun _ msg ->
            async {
                calls.Add msg
                return Ok()
            } }

let private gateway (paidAt: DateTimeOffset) : PaymentGatewayPort =
    { ConfirmPayment = fun _ _ -> async { return Ok paidAt } }

let private baseAmount = { Amount = 400_000L; Currency = JPY }

[<Fact>]
[<Trait("Category", "Integration")>]
let ``精算書を発行して予約 ID で復元できる（法人割引適用・US21/US22/US23）`` () =
    use conn = openDb ()
    let repo = InvoiceRepository.create conn fixedClock
    let calls = System.Collections.Generic.List<string>()

    let invoice =
        Billing.generateInvoice
            repo
            (notifier calls)
            newId
            (bookingId ())
            (corporate ())
            baseAmount
            CorporateStandard
            (DateTimeOffset(2026, 10, 6, 0, 0, 0, TimeSpan.Zero))
        |> Async.RunSynchronously
        |> function
            | Ok i -> i
            | Error e -> failwithf "%A" e

    // 法人 10% 割引 → 360000
    invoice.FinalAmount.Amount |> should equal 360_000L
    calls.Count |> should equal 1

    match repo.FindByBookingId(bookingId ()) |> Async.RunSynchronously with
    | Ok(Some found) ->
        found.FinalAmount.Amount |> should equal 360_000L
        DiscountRate.value found.DiscountRate |> should equal 0.10m
        PaymentState.name found.Payment |> should equal "Pending"
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``同一予約の精算書二重発行は拒否される`` () =
    use conn = openDb ()
    let repo = InvoiceRepository.create conn fixedClock
    let calls = System.Collections.Generic.List<string>()

    let gen () =
        Billing.generateInvoice
            repo
            (notifier calls)
            newId
            (bookingId ())
            (corporate ())
            baseAmount
            CorporateStandard
            (DateTimeOffset(2026, 10, 6, 0, 0, 0, TimeSpan.Zero))
        |> Async.RunSynchronously

    gen () |> Result.isOk |> should equal true

    match gen () with
    | Error(BusinessRuleViolation("AlreadyInvoiced", _)) -> ()
    | other -> failwithf "AlreadyInvoiced を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``入金確認で Confirmed へ遷移し永続化される（US23）`` () =
    use conn = openDb ()
    let repo = InvoiceRepository.create conn fixedClock
    let calls = System.Collections.Generic.List<string>()

    let invoice =
        Billing.generateInvoice
            repo
            (notifier calls)
            newId
            (bookingId ())
            (corporate ())
            baseAmount
            CorporateStandard
            (DateTimeOffset(2026, 10, 6, 0, 0, 0, TimeSpan.Zero))
        |> Async.RunSynchronously
        |> function
            | Ok i -> i
            | Error e -> failwithf "%A" e

    let paidAt = DateTimeOffset(2026, 10, 20, 0, 0, 0, TimeSpan.Zero)

    Billing.confirmPayment repo (gateway paidAt) invoice.InvoiceId
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    match repo.FindByInvoiceId invoice.InvoiceId |> Async.RunSynchronously with
    | Ok(Some found) -> PaymentState.name found.Payment |> should equal "Confirmed"
    | other -> failwithf "Some を期待したが: %A" other
