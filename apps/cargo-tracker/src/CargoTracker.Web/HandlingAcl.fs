/// Handling コンテキストの貨物スナップショット取得 ACL（合成層・BC 分離）。
///
/// 追跡番号 → tracking_activity.booking_id → cargo（出発地/目的地）+ leg（旅程）を DB から解決し、
/// Handling.Domain.CargoSnapshot を組み立てる。Handling ドメインは Booking を直接参照しない。
module CargoTracker.Web.HandlingAcl

open System.Data
open Donald
open CargoTracker.Shared.Domain

/// 追跡番号から貨物スナップショットを解決する CargoSnapshotProvider を生成する。
let cargoSnapshotProvider (conn: IDbConnection) : CargoTracker.Handling.Application.CargoSnapshotProvider =
    { FindByTrackingNumber =
        fun trackingNumber ->
            async {
                try
                    let bookingId =
                        conn
                        |> Db.newCommand "SELECT booking_id FROM tracking_activity WHERE tracking_number = @tn"
                        |> Db.setParams [ "tn", SqlType.String trackingNumber ]
                        |> Db.querySingle (fun rd -> rd.ReadString "booking_id")

                    match bookingId with
                    | None -> return Ok None
                    | Some bid ->
                        let cargo =
                            conn
                            |> Db.newCommand
                                "SELECT origin_unlocode, destination_unlocode FROM cargo WHERE booking_id = @bid"
                            |> Db.setParams [ "bid", SqlType.String bid ]
                            |> Db.querySingle (fun rd ->
                                rd.ReadString "origin_unlocode", rd.ReadString "destination_unlocode")

                        match cargo with
                        | None -> return Ok None
                        | Some(origin, destination) ->
                            let legs =
                                conn
                                |> Db.newCommand
                                    """
                                    SELECT l.load_location_unlocode, l.unload_location_unlocode, l.voyage_number
                                    FROM leg l JOIN cargo c ON c.id = l.cargo_id
                                    WHERE c.booking_id = @bid ORDER BY l.seq_number
                                    """
                                |> Db.setParams [ "bid", SqlType.String bid ]
                                |> Db.query (fun rd ->
                                    { CargoTracker.Handling.Domain.LegSnapshot.LoadLocation =
                                        rd.ReadString "load_location_unlocode"
                                      CargoTracker.Handling.Domain.LegSnapshot.UnloadLocation =
                                        rd.ReadString "unload_location_unlocode"
                                      CargoTracker.Handling.Domain.LegSnapshot.VoyageNumber =
                                        rd.ReadString "voyage_number" })

                            return
                                Ok(
                                    Some
                                        { CargoTracker.Handling.Domain.CargoSnapshot.BookingId = bid
                                          CargoTracker.Handling.Domain.CargoSnapshot.Origin = origin
                                          CargoTracker.Handling.Domain.CargoSnapshot.Destination = destination
                                          CargoTracker.Handling.Domain.CargoSnapshot.ItineraryLegs = legs }
                                )
                with ex ->
                    return Error(BusinessRuleViolation("CargoSnapshotProvider", ex.Message))
            } }
