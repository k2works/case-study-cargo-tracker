namespace CargoTracker.Handling.Infrastructure

open System
open System.Data
open Donald
open CargoTracker.Shared.Domain
open CargoTracker.Handling.Domain
open CargoTracker.Handling.Application

// Handling コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。

/// 荷役履歴の読み取りモデル（US15/US17・一覧表示）。
type HandlingActivityRow =
    { BookingId: string
      HandlingType: string
      Location: string
      CompletionTime: string
      VoyageNumber: string option }

module HandlingQueries =

    /// 荷役作業履歴を新しい順に取得する（US15 一覧）。
    let findAll (conn: IDbConnection) : HandlingActivityRow list =
        conn
        |> Db.newCommand
            """
            SELECT booking_id, event_type, location_unlocode, event_completion_time, voyage_number
            FROM handling_activity
            ORDER BY event_completion_time DESC, id DESC
            """
        |> Db.query (fun rd ->
            { BookingId = rd.ReadString "booking_id"
              HandlingType = rd.ReadString "event_type"
              Location = rd.ReadString "location_unlocode"
              CompletionTime = rd.ReadString "event_completion_time"
              VoyageNumber = rd.ReadStringOption "voyage_number" })

module HandlingRepository =

    let private voyageNumberOf (handlingType: HandlingType) : string option =
        match handlingType with
        | Load v
        | Unload v -> Some(VoyageNumber.value v)
        | Receive
        | Customs
        | Claim -> None

    let create (conn: IDbConnection) (clock: Clock) : HandlingRepository =

        let save (activity: HandlingActivity) : Async<Result<unit, DomainError>> =
            async {
                try
                    let now = (clock ()).UtcDateTime.ToString("o")

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO handling_activity
                            (booking_id, event_type, event_completion_time, location_unlocode,
                             voyage_number, consignee_confirmation, created_at, updated_at, version)
                        VALUES
                            (@booking_id, @event_type, @completion_time, @location,
                             @voyage_number, @consignee_confirmation, @now, @now, 0)
                        """
                    |> Db.setParams
                        [ "booking_id", SqlType.String(CargoBookingId.value activity.CargoBookingId)
                          "event_type", SqlType.String(HandlingType.toString activity.Type)
                          "completion_time", SqlType.String(activity.CompletionTime.UtcDateTime.ToString("o"))
                          "location", SqlType.String(Location.value activity.Location)
                          "voyage_number",
                          (match voyageNumberOf activity.Type with
                           | Some v -> SqlType.String v
                           | None -> SqlType.Null)
                          "consignee_confirmation",
                          (match activity.ConsigneeConfirmation with
                           | Some c -> SqlType.String c
                           | None -> SqlType.Null)
                          "now", SqlType.String now ]
                    |> Db.exec

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("HandlingRepository", ex.Message))
            }

        { Save = save }
