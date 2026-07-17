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

/// 追跡例外の読み取りモデル（US19/US20）。Index は新しい順の位置（解決アクション対象）。
type TrackingExceptionView =
    { Index: int
      ExceptionType: string
      Location: string
      OccurredAt: string
      Description: string
      Escalated: bool
      Resolved: bool }

type TrackingView =
    { TrackingNumber: string
      BookingId: string
      TransportStatus: string
      Events: TrackingEventView list
      Exceptions: TrackingExceptionView list }

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

    /// 例外を新しい順（seq DESC）で読み込む。Index は 0 始まりで register の prepend 順に一致する。
    let private exceptionsOf (conn: IDbConnection) (trackingId: int64) : TrackingExceptionView list =
        conn
        |> Db.newCommand
            """
            SELECT exception_type, location_unlocode, occurred_at, description, escalation_flag, resolved_at
            FROM tracking_exception_event
            WHERE tracking_id = @tracking_id
            ORDER BY seq_number DESC
            """
        |> Db.setParams [ "tracking_id", SqlType.Int64 trackingId ]
        |> Db.query (fun rd ->
            rd.ReadString "exception_type",
            rd.ReadStringOption "location_unlocode" |> Option.defaultValue "",
            rd.ReadString "occurred_at",
            rd.ReadStringOption "description" |> Option.defaultValue "",
            rd.ReadBoolean "escalation_flag",
            rd.ReadStringOption "resolved_at")
        |> List.mapi (fun i (exType, loc, occurredAt, description, escalated, resolvedAt) ->
            { Index = i
              ExceptionType = exType
              Location = loc
              OccurredAt = occurredAt
              Description = description
              Escalated = escalated
              Resolved = Option.isSome resolvedAt })

    let private toView (conn: IDbConnection) (id: int64, tn: string, bid: string, status: string) : TrackingView =
        { TrackingNumber = tn
          BookingId = bid
          TransportStatus = status
          Events = eventsOf conn id
          Exceptions = exceptionsOf conn id }

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

    /// 追跡一覧の要約行（追跡番号・予約 ID・現在の輸送状態）。
    type TrackingSummary =
        { TrackingNumber: string
          BookingId: string
          TransportStatus: string }

    /// 全追跡の要約を取得する（担当者が追跡番号を一覧から選べるようにする・US18 導線改善）。
    let findAllSummary (conn: IDbConnection) : TrackingSummary list =
        conn
        |> Db.newCommand
            "SELECT tracking_number, booking_id, transport_status FROM tracking_activity ORDER BY tracking_number"
        |> Db.query (fun rd ->
            { TrackingNumber = rd.ReadString "tracking_number"
              BookingId = rd.ReadString "booking_id"
              TransportStatus = rd.ReadString "transport_status" })

    /// 予約 ID に未解決の輸送例外（遅延・破損等）が存在するか判定する（US21 受入6 例外時料金調整・合成層向け）。
    let hasUnresolvedException (conn: IDbConnection) (bookingId: string) : bool =
        conn
        |> Db.newCommand
            """
            SELECT COUNT(*) AS cnt
            FROM tracking_exception_event e
            JOIN tracking_activity a ON a.id = e.tracking_id
            WHERE a.booking_id = @bid AND e.resolved_at IS NULL
            """
        |> Db.setParams [ "bid", SqlType.String bookingId ]
        |> Db.querySingle (fun rd -> rd.ReadInt64 "cnt")
        |> Option.map (fun c -> c > 0L)
        |> Option.defaultValue false

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

    /// 生例外行（復元前）。resolved_at が None なら未解決、Some なら解決済み。
    type private ExceptionRow =
        { ExceptionType: string
          Location: string
          OccurredAt: string
          Description: string
          Escalated: bool
          ResolvedAt: string option
          ResolutionNote: string option }

    /// 生レコードから TrackingActivity を復元する（状態は Events / Exceptions から導出）。
    let private reconstruct
        (trackingNumber: string)
        (bookingId: string)
        (rows: EventRow list)
        (exRows: ExceptionRow list)
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

            let! exceptions =
                exRows
                |> List.traverseResultM (fun r ->
                    result {
                        let! exType = ExceptionType.ofString r.ExceptionType

                        let! loc =
                            Location.create r.Location
                            |> Result.mapError (fun m -> ValidationError("Location", m))

                        let occurredAt =
                            DateTimeOffset.Parse(r.OccurredAt, null, Globalization.DateTimeStyles.RoundtripKind)

                        let resolution =
                            match r.ResolvedAt with
                            | Some raw ->
                                Resolved(DateTimeOffset.Parse(raw, null, Globalization.DateTimeStyles.RoundtripKind))
                            | None -> Unresolved r.Escalated

                        return
                            { ExceptionType = exType
                              Location = loc
                              OccurredAt = occurredAt
                              Description = r.Description
                              Resolution = resolution
                              ResolutionNote = r.ResolutionNote }
                    })

            return
                { TrackingNumber = tn
                  BookingId = bid
                  Events = events
                  Exceptions = exceptions }
        }

    let create (conn: IDbConnection) (clock: Clock) : TrackingRepository =

        /// tracking_handling_event を追記（append-only）で永続化する（レビュー中#2）。
        /// イベントは不変・追加のみのため、既に永続化済みの seq_number を超える分だけを INSERT する
        /// （全置換 DELETE→INSERT を廃止し、イベントソースの追記原則と監査整合を守る）。
        let syncEvents (tx: IDbTransaction) (nowStr: string) (trackingNumber: string) (activity: TrackingActivity) =
            let persistedCount =
                conn
                |> Db.newCommand
                    "SELECT COUNT(*) AS cnt FROM tracking_handling_event WHERE tracking_id = (SELECT id FROM tracking_activity WHERE tracking_number = @tn)"
                |> Db.setTransaction tx
                |> Db.setParams [ "tn", SqlType.String trackingNumber ]
                |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")
                |> Option.defaultValue 0

            // Events は新しい順。古い順に並べ、既存の persistedCount 件を除いた新規分のみ追記する。
            activity.Events
            |> List.rev
            |> List.indexed
            |> List.filter (fun (i, _) -> i >= persistedCount)
            |> List.iter (fun (i, event) ->
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

        /// tracking_exception_event を集約ルート経由で全置換する（Exceptions は新しい順→seq は古い順）。
        /// 例外の解決は既存行の resolved_at/escalation_flag/resolution_notes を書き換えるため全置換で確実に反映する。
        /// 注（永続化戦略の非対称・レビュー中#2）: イベント（syncEvents）は不変・追記のみなので append-only、
        /// 例外は「登録は追記・解決は既存行の更新」という可変性があるため全置換とする。差分 UPDATE 化は将来の最適化余地。
        let syncExceptions (tx: IDbTransaction) (nowStr: string) (trackingNumber: string) (activity: TrackingActivity) =
            conn
            |> Db.newCommand
                "DELETE FROM tracking_exception_event WHERE tracking_id = (SELECT id FROM tracking_activity WHERE tracking_number = @tn)"
            |> Db.setTransaction tx
            |> Db.setParams [ "tn", SqlType.String trackingNumber ]
            |> Db.exec

            activity.Exceptions
            |> List.rev
            |> List.iteri (fun i (ex: TrackingException) ->
                let escalated, resolvedAt =
                    match ex.Resolution with
                    | Unresolved e -> e, None
                    | Resolved at -> false, Some(at.UtcDateTime.ToString("o"))

                conn
                |> Db.newCommand
                    """
                    INSERT INTO tracking_exception_event
                        (tracking_id, exception_type, location_unlocode, occurred_at, escalation_flag, description,
                         resolved_at, resolution_notes, seq_number, created_at, updated_at)
                    VALUES
                        ((SELECT id FROM tracking_activity WHERE tracking_number = @tn),
                         @exception_type, @location, @occurred_at, @escalation_flag, @description,
                         @resolved_at, @resolution_notes, @seq_number, @now, @now)
                    """
                |> Db.setTransaction tx
                |> Db.setParams
                    [ "tn", SqlType.String trackingNumber
                      "exception_type", SqlType.String(ExceptionType.toString ex.ExceptionType)
                      "location", SqlType.String(Location.value ex.Location)
                      "occurred_at", SqlType.String(ex.OccurredAt.UtcDateTime.ToString("o"))
                      "escalation_flag", SqlType.Boolean escalated
                      "description", SqlType.String ex.Description
                      "resolved_at",
                      (match resolvedAt with
                       | Some s -> SqlType.String s
                       | None -> SqlType.Null)
                      "resolution_notes",
                      (match ex.ResolutionNote with
                       | Some n -> SqlType.String n
                       | None -> SqlType.Null)
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
                    syncExceptions tx now (TrackingNumber.value activity.TrackingNumber) activity
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
                        syncExceptions tx now tn activity
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

                        // Exceptions は新しい順（seq DESC）で復元する（index が register の prepend 順に一致）。
                        let exRows =
                            conn
                            |> Db.newCommand
                                """
                                SELECT x.exception_type, x.location_unlocode, x.occurred_at, x.description,
                                       x.escalation_flag, x.resolved_at, x.resolution_notes
                                FROM tracking_exception_event x
                                JOIN tracking_activity a ON a.id = x.tracking_id
                                WHERE a.tracking_number = @tn
                                ORDER BY x.seq_number DESC
                                """
                            |> Db.setParams [ "tn", SqlType.String tn ]
                            |> Db.query (fun rd ->
                                { ExceptionType = rd.ReadString "exception_type"
                                  Location = rd.ReadStringOption "location_unlocode" |> Option.defaultValue ""
                                  OccurredAt = rd.ReadString "occurred_at"
                                  Description = rd.ReadStringOption "description" |> Option.defaultValue ""
                                  Escalated = rd.ReadBoolean "escalation_flag"
                                  ResolvedAt = rd.ReadStringOption "resolved_at"
                                  ResolutionNote = rd.ReadStringOption "resolution_notes" })

                        return reconstruct tn bookingId rows exRows |> Result.map Some
                with ex ->
                    return Error(BusinessRuleViolation("TrackingRepository", ex.Message))
            }

        { Save = save
          FindByTrackingNumber = findByTrackingNumber
          Update = update }
