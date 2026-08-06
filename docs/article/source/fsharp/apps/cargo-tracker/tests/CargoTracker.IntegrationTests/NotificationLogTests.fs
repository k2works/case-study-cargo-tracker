module CargoTracker.IntegrationTests.NotificationLogTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Booking.Domain
open CargoTracker.Booking.Application
open CargoTracker.Booking.Infrastructure

// US12: 荷主通知（notification_log 記録）の統合テスト。

let private notificationDdl =
    """
    CREATE TABLE notification_log (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        booking_id  TEXT    NOT NULL,
        recipient   TEXT    NOT NULL,
        message     TEXT    NOT NULL,
        notified_at TEXT    NOT NULL,
        created_at  TEXT    NOT NULL
    );
    """

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- notificationDdl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: CargoTracker.Shared.Domain.Clock =
    fun () -> DateTimeOffset(2026, 8, 20, 0, 0, 0, TimeSpan.Zero)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷主通知アダプタは notification_log に記録する`` () =
    use conn = openDb ()
    let notifier = NotificationLogShipperNotifier.create conn fixedClock
    let bid = BookingId.ofString "BKG-0001"

    notifier.Notify bid "recipient-uuid" "予約 BKG-0001 の確定経路: JPTYO→USLAX"
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT COUNT(*) FROM notification_log WHERE booking_id = 'BKG-0001'"
    cmd.ExecuteScalar() |> Convert.ToInt32 |> should equal 1
