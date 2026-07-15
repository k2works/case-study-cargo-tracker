namespace CargoTracker.Routing.Infrastructure

open System
open System.Data
open Donald
open FsToolkit.ErrorHandling
open CargoTracker.Shared.Domain
open CargoTracker.Routing.Domain
open CargoTracker.Routing.Application

// Routing コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// voyage（親）と carrier_movement（子）の 1 対多を単一トランザクションで保存する。
// ANSI 標準の範囲で SQL を記述し、SQLite / PostgreSQL 両方言で動作させる（ADR-0003）。

/// 航海一覧の読み取りモデル（CQRS Read 側）。
type VoyageListItem =
    { VoyageNumber: string
      VesselName: string
      CarrierName: string
      Origin: string
      Destination: string
      DepartureDate: string
      ArrivalDate: string }

module VoyageRepository =

    let private cargoTypesToString (tags: Set<CargoTypeTag>) : string =
        tags |> Set.toList |> List.map CargoTypeTag.toString |> String.concat ","

    let private cargoTypesOfString (value: string) : Result<Set<CargoTypeTag>, DomainError> =
        value.Split(',')
        |> Array.toList
        |> List.filter (fun s -> not (String.IsNullOrWhiteSpace s))
        |> List.map CargoTypeTag.ofString
        |> List.fold
            (fun acc r ->
                match acc, r with
                | Ok xs, Ok x -> Ok(x :: xs)
                | Error e, _ -> Error e
                | _, Error e -> Error e)
            (Ok [])
        |> Result.map Set.ofList

    /// carrier_movement の生行。
    type private MovementRow =
        { Departure: string
          Arrival: string
          DepartureDate: string
          ArrivalDate: string
          SeqNumber: int }

    let private parseDate (s: string) = DateTimeOffset.Parse(s)

    /// 生行から Voyage 集約を復元する。
    let private reconstruct
        (voyageNumber: string)
        (vesselName: string)
        (carrierName: string)
        (cargoTypes: string)
        (movementRows: MovementRow list)
        : Result<Voyage, DomainError> =
        result {
            let toLoc field code =
                Location.create code |> Result.mapError (fun m -> ValidationError(field, m))

            let! vn = VoyageNumber.create voyageNumber
            let! vessel = VesselName.create vesselName
            let! carrier = CarrierName.create carrierName
            let! tags = cargoTypesOfString cargoTypes

            let! movements =
                movementRows
                |> List.sortBy (fun r -> r.SeqNumber)
                |> List.map (fun r ->
                    result {
                        let! dep = toLoc "DepartureLocation" r.Departure
                        let! arr = toLoc "ArrivalLocation" r.Arrival

                        return!
                            CarrierMovement.create
                                dep
                                arr
                                (parseDate r.DepartureDate)
                                (parseDate r.ArrivalDate)
                                r.SeqNumber
                    })
                |> List.fold
                    (fun acc r ->
                        match acc, r with
                        | Ok xs, Ok x -> Ok(xs @ [ x ])
                        | Error e, _ -> Error e
                        | _, Error e -> Error e)
                    (Ok [])

            let! schedule = Schedule.create movements

            return
                { VoyageNumber = vn
                  Vessel = vessel
                  Carrier = carrier
                  Schedule = schedule
                  SupportedCargoTypes = tags }
        }

    /// carrier_movement を親 voyage_id に紐付けて書き込む。
    let private insertMovements
        (conn: IDbConnection)
        (tx: IDbTransaction)
        (voyageId: int64)
        (voyage: Voyage)
        (now: string)
        =
        Schedule.movements voyage.Schedule
        |> List.iter (fun m ->
            conn
            |> Db.newCommand
                """
                INSERT INTO carrier_movement
                    (voyage_id, departure_location_unlocode, arrival_location_unlocode,
                     departure_date, arrival_date, seq_number, created_at, updated_at)
                VALUES
                    (@voyage_id, @departure, @arrival, @departure_date, @arrival_date, @seq_number, @now, @now)
                """
            |> Db.setTransaction tx
            |> Db.setParams
                [ "voyage_id", SqlType.Int64 voyageId
                  "departure", SqlType.String(Location.value (CarrierMovement.departureLocation m))
                  "arrival", SqlType.String(Location.value (CarrierMovement.arrivalLocation m))
                  "departure_date", SqlType.String((CarrierMovement.departureDate m).UtcDateTime.ToString("o"))
                  "arrival_date", SqlType.String((CarrierMovement.arrivalDate m).UtcDateTime.ToString("o"))
                  "seq_number", SqlType.Int32(CarrierMovement.seqNumber m)
                  "now", SqlType.String now ]
            |> Db.exec)

    let private voyageIdByNumber (conn: IDbConnection) (tx: IDbTransaction) (voyageNumber: string) : int64 =
        conn
        |> Db.newCommand "SELECT id AS vid FROM voyage WHERE voyage_number = @vn"
        |> Db.setTransaction tx
        |> Db.setParams [ "vn", SqlType.String voyageNumber ]
        |> Db.querySingle (fun rd -> rd.ReadInt64 "vid")
        |> Option.defaultValue 0L

    /// Donald の出力ポート実装を生成する。
    let create (conn: IDbConnection) (clock: Clock) : VoyageRepository =

        let save (voyage: Voyage) : Async<Result<unit, DomainError>> =
            async {
                use tx = conn.BeginTransaction()

                try
                    let now = (clock ()).UtcDateTime.ToString("o")

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO voyage
                            (voyage_number, vessel_name, carrier_name, supported_cargo_types, created_at, updated_at, version)
                        VALUES
                            (@voyage_number, @vessel_name, @carrier_name, @cargo_types, @now, @now, 0)
                        """
                    |> Db.setTransaction tx
                    |> Db.setParams
                        [ "voyage_number", SqlType.String(VoyageNumber.value voyage.VoyageNumber)
                          "vessel_name", SqlType.String(VesselName.value voyage.Vessel)
                          "carrier_name", SqlType.String(CarrierName.value voyage.Carrier)
                          "cargo_types", SqlType.String(cargoTypesToString voyage.SupportedCargoTypes)
                          "now", SqlType.String now ]
                    |> Db.exec

                    let voyageId = voyageIdByNumber conn tx (VoyageNumber.value voyage.VoyageNumber)
                    insertMovements conn tx voyageId voyage now

                    tx.Commit()
                    return Ok()
                with ex ->
                    tx.Rollback()
                    return Error(BusinessRuleViolation("VoyageRepository", ex.Message))
            }

        let update (voyage: Voyage) : Async<Result<unit, DomainError>> =
            async {
                use tx = conn.BeginTransaction()

                try
                    let now = (clock ()).UtcDateTime.ToString("o")
                    let vnStr = VoyageNumber.value voyage.VoyageNumber
                    let voyageId = voyageIdByNumber conn tx vnStr

                    if voyageId = 0L then
                        tx.Rollback()
                        return Error(NotFound("Voyage", vnStr))
                    else
                        conn
                        |> Db.newCommand
                            """
                            UPDATE voyage
                            SET vessel_name = @vessel_name, carrier_name = @carrier_name,
                                supported_cargo_types = @cargo_types, updated_at = @now, version = version + 1
                            WHERE id = @id
                            """
                        |> Db.setTransaction tx
                        |> Db.setParams
                            [ "vessel_name", SqlType.String(VesselName.value voyage.Vessel)
                              "carrier_name", SqlType.String(CarrierName.value voyage.Carrier)
                              "cargo_types", SqlType.String(cargoTypesToString voyage.SupportedCargoTypes)
                              "now", SqlType.String now
                              "id", SqlType.Int64 voyageId ]
                        |> Db.exec

                        // 運送区間は総入れ替え（子の削除→再挿入）。
                        conn
                        |> Db.newCommand "DELETE FROM carrier_movement WHERE voyage_id = @id"
                        |> Db.setTransaction tx
                        |> Db.setParams [ "id", SqlType.Int64 voyageId ]
                        |> Db.exec

                        insertMovements conn tx voyageId voyage now

                        tx.Commit()
                        return Ok()
                with ex ->
                    tx.Rollback()
                    return Error(BusinessRuleViolation("VoyageRepository", ex.Message))
            }

        let loadMovements (voyageId: int64) : MovementRow list =
            conn
            |> Db.newCommand
                """
                SELECT departure_location_unlocode, arrival_location_unlocode,
                       departure_date, arrival_date, seq_number
                FROM carrier_movement WHERE voyage_id = @id ORDER BY seq_number
                """
            |> Db.setParams [ "id", SqlType.Int64 voyageId ]
            |> Db.query (fun rd ->
                { Departure = rd.ReadString "departure_location_unlocode"
                  Arrival = rd.ReadString "arrival_location_unlocode"
                  DepartureDate = rd.ReadString "departure_date"
                  ArrivalDate = rd.ReadString "arrival_date"
                  SeqNumber = rd.ReadInt32 "seq_number" })

        let findByNumber (voyageNumber: VoyageNumber) : Async<Result<Voyage option, DomainError>> =
            async {
                try
                    let vnStr = VoyageNumber.value voyageNumber

                    let header =
                        conn
                        |> Db.newCommand
                            "SELECT id, voyage_number, vessel_name, carrier_name, supported_cargo_types FROM voyage WHERE voyage_number = @vn"
                        |> Db.setParams [ "vn", SqlType.String vnStr ]
                        |> Db.querySingle (fun rd ->
                            rd.ReadInt64 "id",
                            rd.ReadString "voyage_number",
                            rd.ReadString "vessel_name",
                            rd.ReadString "carrier_name",
                            rd.ReadString "supported_cargo_types")

                    match header with
                    | None -> return Ok None
                    | Some(vid, vn, vessel, carrier, tags) ->
                        let movements = loadMovements vid
                        return reconstruct vn vessel carrier tags movements |> Result.map Some
                with ex ->
                    return Error(BusinessRuleViolation("VoyageRepository", ex.Message))
            }

        let findAll () : Async<Result<Voyage list, DomainError>> =
            async {
                try
                    let headers =
                        conn
                        |> Db.newCommand
                            "SELECT id, voyage_number, vessel_name, carrier_name, supported_cargo_types FROM voyage ORDER BY voyage_number"
                        |> Db.query (fun rd ->
                            rd.ReadInt64 "id",
                            rd.ReadString "voyage_number",
                            rd.ReadString "vessel_name",
                            rd.ReadString "carrier_name",
                            rd.ReadString "supported_cargo_types")

                    let result =
                        headers
                        |> List.fold
                            (fun acc (vid, vn, vessel, carrier, tags) ->
                                match acc with
                                | Error e -> Error e
                                | Ok xs ->
                                    let movements = loadMovements vid

                                    match reconstruct vn vessel carrier tags movements with
                                    | Ok v -> Ok(xs @ [ v ])
                                    | Error e -> Error e)
                            (Ok [])

                    return result
                with ex ->
                    return Error(BusinessRuleViolation("VoyageRepository", ex.Message))
            }

        { Save = save
          Update = update
          FindByNumber = findByNumber
          FindAll = findAll }
