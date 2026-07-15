module CargoTracker.IntegrationTests.BookingEventConsumerTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Web

// US14: 予約確定（BookingConfirmed）→ 追跡番号自動発行の BC 連携（合成層消費）。

let private ddl =
    """
    CREATE TABLE tracking_activity (
        id INTEGER PRIMARY KEY AUTOINCREMENT, tracking_number TEXT NOT NULL UNIQUE,
        booking_id TEXT NOT NULL, transport_status TEXT NOT NULL DEFAULT 'NOT_RECEIVED',
        access_token TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
        version INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE tracking_handling_event (
        id INTEGER PRIMARY KEY AUTOINCREMENT, tracking_id INTEGER NOT NULL,
        event_type TEXT NOT NULL, event_time TEXT NOT NULL, location_unlocode TEXT,
        voyage_number TEXT, seq_number INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
    );
    CREATE TABLE notification_log (
        id INTEGER PRIMARY KEY AUTOINCREMENT, booking_id TEXT NOT NULL, recipient TEXT NOT NULL,
        message TEXT NOT NULL, notified_at TEXT NOT NULL, created_at TEXT NOT NULL
    );
    """

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- ddl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private clock: Clock =
    fun () -> DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)

let private newId () : Guid = Guid.NewGuid()

let private countTracking (conn: IDbConnection) (bookingId: string) : int =
    use cmd = conn.CreateCommand()
    cmd.CommandText <- sprintf "SELECT COUNT(*) FROM tracking_activity WHERE booking_id = '%s'" bookingId
    cmd.ExecuteScalar() |> Convert.ToInt32

[<Fact>]
[<Trait("Category", "Integration")>]
let ``BookingConfirmed を消費すると追跡番号が発行される（US14）`` () =
    use conn = openDb ()
    let dispatcher = BookingEventConsumer.create conn clock newId
    let bookingId = CargoTracker.Booking.Domain.BookingId.ofString "BKG-0001"

    dispatcher.Dispatch(CargoTracker.Booking.Domain.BookingConfirmed bookingId)
    |> Async.RunSynchronously

    countTracking conn "BKG-0001" |> should equal 1

[<Fact>]
[<Trait("Category", "Integration")>]
let ``同じ予約の再確認では追跡番号を二重発行しない（冪等）`` () =
    use conn = openDb ()
    let dispatcher = BookingEventConsumer.create conn clock newId
    let bookingId = CargoTracker.Booking.Domain.BookingId.ofString "BKG-0001"

    dispatcher.Dispatch(CargoTracker.Booking.Domain.BookingConfirmed bookingId)
    |> Async.RunSynchronously

    dispatcher.Dispatch(CargoTracker.Booking.Domain.BookingConfirmed bookingId)
    |> Async.RunSynchronously

    countTracking conn "BKG-0001" |> should equal 1

[<Fact>]
[<Trait("Category", "Integration")>]
let ``確定以外のイベントでは追跡番号を発行しない`` () =
    use conn = openDb ()
    let dispatcher = BookingEventConsumer.create conn clock newId
    let bookingId = CargoTracker.Booking.Domain.BookingId.ofString "BKG-0001"

    dispatcher.Dispatch(CargoTracker.Booking.Domain.BookingRestoredToRouting bookingId)
    |> Async.RunSynchronously

    countTracking conn "BKG-0001" |> should equal 0
