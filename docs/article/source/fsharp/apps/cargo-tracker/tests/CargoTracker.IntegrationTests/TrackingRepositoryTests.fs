module CargoTracker.IntegrationTests.TrackingRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Tracking.Domain
open CargoTracker.Tracking.Infrastructure

// US14/US15/US18: TrackingRepository（Donald）と読み取りモデル（TrackingQueries）の統合テスト。

let private ddl =
    """
    CREATE TABLE tracking_activity (
        id               INTEGER PRIMARY KEY AUTOINCREMENT,
        tracking_number  TEXT    NOT NULL UNIQUE,
        booking_id       TEXT    NOT NULL,
        transport_status TEXT    NOT NULL DEFAULT 'NOT_RECEIVED',
        access_token     TEXT    NOT NULL UNIQUE,
        created_at       TEXT    NOT NULL,
        updated_at       TEXT    NOT NULL,
        version          INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE tracking_handling_event (
        id                INTEGER PRIMARY KEY AUTOINCREMENT,
        tracking_id       INTEGER NOT NULL REFERENCES tracking_activity(id),
        event_type        TEXT    NOT NULL,
        event_time        TEXT    NOT NULL,
        location_unlocode TEXT,
        voyage_number     TEXT,
        seq_number        INTEGER NOT NULL,
        created_at        TEXT    NOT NULL,
        updated_at        TEXT    NOT NULL
    );
    CREATE TABLE tracking_exception_event (
        id               INTEGER PRIMARY KEY AUTOINCREMENT,
        tracking_id      INTEGER NOT NULL REFERENCES tracking_activity(id),
        exception_type   TEXT    NOT NULL,
        location_unlocode TEXT,
        occurred_at      TEXT    NOT NULL,
        escalation_flag  INTEGER NOT NULL DEFAULT 0,
        description      TEXT,
        resolved_at      TEXT,
        resolution_notes TEXT,
        seq_number       INTEGER NOT NULL,
        created_at       TEXT    NOT NULL,
        updated_at       TEXT    NOT NULL
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
    fun () -> DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)

let private newId () : Guid = Guid.NewGuid()

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%s" e

let private bookingId () =
    match TrackingBookingId.create "BKG-0001" with
    | Ok b -> b
    | Error e -> failwithf "%A" e

let private event etype code day =
    { EventType = etype
      Location = loc code
      CompletionTime = DateTimeOffset(2026, 9, day, 0, 0, 0, TimeSpan.Zero) }

[<Fact>]
[<Trait("Category", "Integration")>]
let ``追跡活動を保存して追跡番号で復元できる（NotReceived）`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock
    let activity, _ = TrackingActivity.issue newId (bookingId ())

    repo.Save activity "TOKEN-ABC"
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    match repo.FindByTrackingNumber activity.TrackingNumber |> Async.RunSynchronously with
    | Ok(Some found) -> TrackingActivity.currentStatus found |> should equal NotReceived
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``イベント記録後に更新すると導出状態が往復する`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock
    let activity, _ = TrackingActivity.issue newId (bookingId ())
    repo.Save activity "TOKEN-ABC" |> Async.RunSynchronously |> ignore

    // 受領 → 積込
    let progressed =
        [ event ReceivedEvent "JPTYO" 1; event LoadedEvent "JPTYO" 2 ]
        |> List.fold
            (fun acc e ->
                match TrackingActivity.execute acc (RecordEvent e) with
                | Ok(a, _) -> a
                | Error err -> failwithf "%A" err)
            activity

    repo.Update progressed
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    match repo.FindByTrackingNumber activity.TrackingNumber |> Async.RunSynchronously with
    | Ok(Some found) ->
        TrackingActivity.currentStatus found |> should equal Loaded
        found.Events |> List.length |> should equal 2
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``例外を登録・更新すると InException 状態が永続化され復元される（US19/US20）`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock
    let activity, _ = TrackingActivity.issue newId (bookingId ())
    repo.Save activity "TOKEN-ABC" |> Async.RunSynchronously |> ignore

    // 紛失例外を登録（escalated=true）
    let withEx =
        match
            TrackingActivity.execute
                activity
                (RegisterException(Lost, loc "USLAX", DateTimeOffset(2026, 9, 3, 0, 0, 0, TimeSpan.Zero), "海上事故"))
        with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    repo.Update withEx |> Async.RunSynchronously |> Result.isOk |> should equal true

    match repo.FindByTrackingNumber activity.TrackingNumber |> Async.RunSynchronously with
    | Ok(Some found) ->
        TrackingActivity.currentStatus found |> should equal InException
        found.Exceptions |> List.length |> should equal 1

        match found.Exceptions with
        | [ { ExceptionType = Lost
              Resolution = Unresolved escalated } ] -> escalated |> should equal true
        | other -> failwithf "Unresolved(true) の Lost を期待したが: %A" other
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``例外解決後は状態が復帰し解決済みが永続化される`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock
    let activity, _ = TrackingActivity.issue newId (bookingId ())
    repo.Save activity "TOKEN-ABC" |> Async.RunSynchronously |> ignore

    let withEx =
        match
            TrackingActivity.execute
                activity
                (RegisterException(Delay, loc "USLAX", DateTimeOffset(2026, 9, 3, 0, 0, 0, TimeSpan.Zero), "遅延"))
        with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    repo.Update withEx |> Async.RunSynchronously |> ignore

    let resolved =
        match
            TrackingActivity.execute
                withEx
                (ResolveException(0, DateTimeOffset(2026, 9, 4, 0, 0, 0, TimeSpan.Zero), "代替手配・補償対応済"))
        with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    repo.Update resolved
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    match repo.FindByTrackingNumber activity.TrackingNumber |> Async.RunSynchronously with
    | Ok(Some found) ->
        // イベントなし＋解決済み例外のみ → NotReceived へ復帰
        TrackingActivity.currentStatus found |> should equal NotReceived

        match found.Exceptions with
        | [ { Resolution = Resolved _
              ResolutionNote = Some note } ] ->
            // 対応内容（US19「対応報告」）が永続化・復元される（レビュー高#1・データ損失防止）
            note |> should equal "代替手配・補償対応済"
        | other -> failwithf "Resolved かつ ResolutionNote=Some を期待したが: %A" other
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``公開トークンで追跡ビューを照会できる（US18）`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock
    let activity, _ = TrackingActivity.issue newId (bookingId ())
    repo.Save activity "TOKEN-XYZ" |> Async.RunSynchronously |> ignore

    let progressed =
        match TrackingActivity.execute activity (RecordEvent(event ReceivedEvent "JPTYO" 1)) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    repo.Update progressed |> Async.RunSynchronously |> ignore

    match TrackingQueries.findByAccessToken conn "TOKEN-XYZ" with
    | Some view ->
        view.TransportStatus |> should equal "RECEIVED"
        view.Events |> List.length |> should equal 1
    | None -> failwith "公開トークンで照会できるはず"

[<Fact>]
[<Trait("Category", "Integration")>]
let ``存在しない追跡番号は None を返す`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock

    match
        repo.FindByTrackingNumber(TrackingNumber.ofString "TRK-NOPE")
        |> Async.RunSynchronously
    with
    | Ok None -> ()
    | other -> failwithf "None を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``不正な日時を持つ行の復元は例外を投げず Error を返す（レビュー中#6）`` () =
    use conn = openDb ()
    let repo = TrackingRepository.create conn fixedClock
    let activity, _ = TrackingActivity.issue newId (bookingId ())
    repo.Save activity "TOKEN-ABC" |> Async.RunSynchronously |> ignore

    // 復元時に DateTimeOffset.Parse が失敗する不正な event_time を直接投入する。
    use cmd = conn.CreateCommand()

    cmd.CommandText <-
        sprintf
            """
            INSERT INTO tracking_handling_event
                (tracking_id, event_type, event_time, location_unlocode, seq_number, created_at, updated_at)
            VALUES ((SELECT id FROM tracking_activity WHERE tracking_number = '%s'),
                    'RECEIVED', 'not-a-date', 'JPTYO', 1, '2026-09-08', '2026-09-08')
            """
            (TrackingNumber.value activity.TrackingNumber)

    cmd.ExecuteNonQuery() |> ignore

    // 例外で落ちず、Error（BusinessRuleViolation）として扱われる。
    match repo.FindByTrackingNumber activity.TrackingNumber |> Async.RunSynchronously with
    | Error(BusinessRuleViolation("TrackingRepository", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other
