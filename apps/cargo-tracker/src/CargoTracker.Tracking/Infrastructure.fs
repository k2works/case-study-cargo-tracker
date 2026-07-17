namespace CargoTracker.Tracking.Infrastructure

open System
open System.Data
open Donald
open FsToolkit.ErrorHandling
open CargoTracker.Shared.Domain
open CargoTracker.Tracking.Domain
open CargoTracker.Tracking.Application

// Tracking コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// tracking_activity（親）と tracking_handling_event（子）を単一トランザクションで永続化する。
// 状態は保持せず、復元時はイベントから currentStatus で導出する（transport_status は非正規化キャッシュ）。

/// 追跡照会の読み取りモデル（US18・CQRS Read 側）。
type TrackingEventView =
    { EventType: string
      Location: string
      EventTime: string }

type TrackingView =
    { TrackingNumber: string
      BookingId: string
      TransportStatus: string
      Events: TrackingEventView list }

module TrackingQueries =

    let private eventsOf (conn: IDbConnection) (trackingId: int64) : TrackingEventView list =
        conn
        |> Db.newCommand
            """
            SELECT event_type, location_unlocode, event_time
            FROM tracking_handling_event
            WHERE tracking_id = @tracking_id
            ORDER BY seq_number
            """
        |> Db.setParams [ "tracking_id", SqlType.Int64 trackingId ]
        |> Db.query (fun rd ->
            { EventType = rd.ReadString "event_type"
              Location = rd.ReadStringOption "location_unlocode" |> Option.defaultValue ""
              EventTime = rd.ReadString "event_time" })

    let private toView (conn: IDbConnection) (id: int64, tn: string, bid: string, status: string) : TrackingView =
        { TrackingNumber = tn
          BookingId = bid
          TransportStatus = status
          Events = eventsOf conn id }

    /// 追跡番号で照会する（US18・認証あり）。
    let findByTrackingNumber (conn: IDbConnection) (trackingNumber: string) : TrackingView option =
        conn
        |> Db.newCommand
            "SELECT id, tracking_number, booking_id, transport_status FROM tracking_activity WHERE tracking_number = @tn"
        |> Db.setParams [ "tn", SqlType.String trackingNumber ]
        |> Db.querySingle (fun rd ->
            rd.ReadInt64 "id",
            rd.ReadString "tracking_number",
            rd.ReadString "booking_id",
            rd.ReadString "transport_status")
        |> Option.map (toView conn)

    /// 公開トークンで照会する（US18・未認証）。
    let findByAccessToken (conn: IDbConnection) (accessToken: string) : TrackingView option =
        conn
        |> Db.newCommand
            "SELECT id, tracking_number, booking_id, transport_status FROM tracking_activity WHERE access_token = @token"
        |> Db.setParams [ "token", SqlType.String accessToken ]
        |> Db.querySingle (fun rd ->
            rd.ReadInt64 "id",
            rd.ReadString "tracking_number",
            rd.ReadString "booking_id",
            rd.ReadString "transport_status")
        |> Option.map (toView conn)

module TrackingRepository =

    /// 生イベント行（復元前）。
    type private EventRow =
        { EventType: string
          Location: string
          EventTime: string }

    /// 生レコードから TrackingActivity を復元する（状態は Events から導出）。
    let private reconstruct
        (trackingNumber: string)
        (bookingId: string)
        (rows: EventRow list)
        : Result<TrackingActivity, DomainError> =
        result {
            let tn = TrackingNumber.ofString trackingNumber
            let! bid = TrackingBookingId.create bookingId

            let! events =
                rows
                |> List.traverseResultM (fun r ->
                    result {
                        let! etype = TrackingEventType.ofString r.EventType

                        let! loc =
                            Location.create r.Location
                            |> Result.mapError (fun m -> ValidationError("Location", m))

                        let time =
                            DateTimeOffset.Parse(r.EventTime, null, Globalization.DateTimeStyles.RoundtripKind)

                        return
                            { EventType = etype
                              Location = loc
                              CompletionTime = time }
                    })

            return
                { TrackingNumber = tn
                  BookingId = bid
                  Events = events
                  // TODO(IT6): tracking_exception_event からの例外復元を実装する。
                  Exceptions = [] }
        }

    let create (conn: IDbConnection) (clock: Clock) : TrackingRepository =

        /// tracking_handling_event を集約ルート経由で全置換する（Events は新しい順→seq は古い順）。
        let syncEvents (tx: IDbTransaction) (nowStr: string) (trackingNumber: string) (activity: TrackingActivity) =
            conn
            |> Db.newCommand
                "DELETE FROM tracking_handling_event WHERE tracking_id = (SELECT id FROM tracking_activity WHERE tracking_number = @tn)"
            |> Db.setTransaction tx
            |> Db.setParams [ "tn", SqlType.String trackingNumber ]
            |> Db.exec

            activity.Events
            |> List.rev
            |> List.iteri (fun i event ->
                conn
                |> Db.newCommand
                    """
                    INSERT INTO tracking_handling_event
                        (tracking_id, event_type, event_time, location_unlocode, seq_number, created_at, updated_at)
                    VALUES
                        ((SELECT id FROM tracking_activity WHERE tracking_number = @tn),
                         @event_type, @event_time, @location, @seq_number, @now, @now)
                    """
                |> Db.setTransaction tx
                |> Db.setParams
                    [ "tn", SqlType.String trackingNumber
                      "event_type", SqlType.String(TrackingEventType.toString event.EventType)
                      "event_time", SqlType.String(event.CompletionTime.UtcDateTime.ToString("o"))
                      "location", SqlType.String(Location.value event.Location)
                      "seq_number", SqlType.Int(i + 1)
                      "now", SqlType.String nowStr ]
                |> Db.setTransaction tx
                |> Db.exec)

        let save (activity: TrackingActivity) (accessToken: string) : Async<Result<unit, DomainError>> =
            async {
                use tx = conn.BeginTransaction()

                try
                    let now = (clock ()).UtcDateTime.ToString("o")

                    let status =
                        TrackingActivity.currentStatus activity |> TrackingActivity.toTransportStatus

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO tracking_activity
                            (tracking_number, booking_id, transport_status, access_token, created_at, updated_at, version)
                        VALUES (@tn, @booking_id, @status, @token, @now, @now, 0)
                        """
                    |> Db.setTransaction tx
                    |> Db.setParams
                        [ "tn", SqlType.String(TrackingNumber.value activity.TrackingNumber)
                          "booking_id", SqlType.String(TrackingBookingId.value activity.BookingId)
                          "status", SqlType.String(TransportStatus.toString status)
                          "token", SqlType.String accessToken
                          "now", SqlType.String now ]
                    |> Db.setTransaction tx
                    |> Db.exec

                    syncEvents tx now (TrackingNumber.value activity.TrackingNumber) activity
                    tx.Commit()
                    return Ok()
                with ex ->
                    tx.Rollback()
                    return Error(BusinessRuleViolation("TrackingRepository", ex.Message))
            }

        let update (activity: TrackingActivity) : Async<Result<unit, DomainError>> =
            async {
                use tx = conn.BeginTransaction()

                try
                    let now = (clock ()).UtcDateTime.ToString("o")
                    let tn = TrackingNumber.value activity.TrackingNumber

                    let status =
                        TrackingActivity.currentStatus activity |> TrackingActivity.toTransportStatus

                    let existing =
                        conn
                        |> Db.newCommand "SELECT COUNT(*) AS cnt FROM tracking_activity WHERE tracking_number = @tn"
                        |> Db.setTransaction tx
                        |> Db.setParams [ "tn", SqlType.String tn ]
                        |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")
                        |> Option.defaultValue 0

                    if existing = 0 then
                        tx.Rollback()
                        return Error(NotFound("TrackingActivity", tn))
                    else
                        conn
                        |> Db.newCommand
                            "UPDATE tracking_activity SET transport_status = @status, updated_at = @now, version = version + 1 WHERE tracking_number = @tn"
                        |> Db.setTransaction tx
                        |> Db.setParams
                            [ "status", SqlType.String(TransportStatus.toString status)
                              "now", SqlType.String now
                              "tn", SqlType.String tn ]
                        |> Db.exec

                        syncEvents tx now tn activity
                        tx.Commit()
                        return Ok()
                with ex ->
                    tx.Rollback()
                    return Error(BusinessRuleViolation("TrackingRepository", ex.Message))
            }

        let findByTrackingNumber
            (trackingNumber: TrackingNumber)
            : Async<Result<TrackingActivity option, DomainError>> =
            async {
                try
                    let tn = TrackingNumber.value trackingNumber

                    let header =
                        conn
                        |> Db.newCommand "SELECT booking_id FROM tracking_activity WHERE tracking_number = @tn"
                        |> Db.setParams [ "tn", SqlType.String tn ]
                        |> Db.querySingle (fun rd -> rd.ReadString "booking_id")

                    match header with
                    | None -> return Ok None
                    | Some bookingId ->
                        // Events は新しい順（seq DESC）で復元する。
                        let rows =
                            conn
                            |> Db.newCommand
                                """
                                SELECT e.event_type, e.location_unlocode, e.event_time
                                FROM tracking_handling_event e
                                JOIN tracking_activity a ON a.id = e.tracking_id
                                WHERE a.tracking_number = @tn
                                ORDER BY e.seq_number DESC
                                """
                            |> Db.setParams [ "tn", SqlType.String tn ]
                            |> Db.query (fun rd ->
                                { EventType = rd.ReadString "event_type"
                                  Location = rd.ReadStringOption "location_unlocode" |> Option.defaultValue ""
                                  EventTime = rd.ReadString "event_time" })

                        return reconstruct tn bookingId rows |> Result.map Some
                with ex ->
                    return Error(BusinessRuleViolation("TrackingRepository", ex.Message))
            }

        { Save = save
          FindByTrackingNumber = findByTrackingNumber
          Update = update }
