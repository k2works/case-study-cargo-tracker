namespace CargoTracker.Booking.Infrastructure

open System
open System.Data
open Donald
open FsToolkit.ErrorHandling
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain
open CargoTracker.Booking.Application

// Booking コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// cargo テーブルへの単一トランザクション書き込みで集約の原子性を保証する（ADR-0001 / IT1 Try#2）。
// ANSI 標準の範囲で SQL を記述し、SQLite / PostgreSQL 両方言で動作させる（ADR-0003）。

/// 貨物予約一覧の読み取りモデル（CQRS Read 側・DTO 直接射影）。
type CargoListItem =
    { BookingId: string
      ShipperId: string
      CargoType: string
      Origin: string
      Destination: string
      ArrivalDeadline: string
      BookingStatus: string }

module CargoQueries =

    /// 貨物予約一覧を取得する（登録日時の降順）。
    let findAll (conn: IDbConnection) : CargoListItem list =
        conn
        |> Db.newCommand
            """
            SELECT booking_id, shipper_id, cargo_type, origin_unlocode, destination_unlocode,
                   arrival_deadline, booking_status
            FROM cargo
            ORDER BY created_at DESC
            """
        |> Db.query (fun rd ->
            { BookingId = rd.ReadString "booking_id"
              ShipperId = rd.ReadString "shipper_id"
              CargoType = rd.ReadString "cargo_type"
              Origin = rd.ReadString "origin_unlocode"
              Destination = rd.ReadString "destination_unlocode"
              ArrivalDeadline = rd.ReadString "arrival_deadline"
              BookingStatus = rd.ReadString "booking_status" })

/// 経路設計者への通知 ACL のスタブ（US06）。IT2 は無処理で成功を返す。
/// 後続 IT で実通知（メール／画面キュー）に差し替える。
module StubRoutingRequestNotifier =

    let create () : RoutingRequestNotifier =
        { Notify = fun (_bookingId: BookingId) -> async { return Ok() } }

/// 荷主存在確認 ACL のアダプタ（ADR-0008）。Shipper プロジェクトを参照せず、
/// shipper テーブルを shipper_uuid（ShipperId の Guid）で直接照会する（BC 分離）。
module ShipperExistenceAdapter =

    let create (conn: IDbConnection) : ShipperExistenceChecker =
        { Exists =
            fun (shipperId: ShipperId) ->
                async {
                    try
                        let guid = (ShipperId.value shipperId).ToString("D")

                        let count =
                            conn
                            |> Db.newCommand "SELECT COUNT(*) AS cnt FROM shipper WHERE shipper_uuid = @uuid"
                            |> Db.setParams [ "uuid", SqlType.String guid ]
                            |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")

                        return Ok(count |> Option.defaultValue 0 > 0)
                    with ex ->
                        return Error(BusinessRuleViolation("ShipperExistenceChecker", ex.Message))
                } }

module CargoRepository =

    /// CargoType を永続化用の (種別文字列, 危険物 3 項目 option, 温度 3 項目 option) に展開する。
    let private explodeCargoType
        (cargoType: CargoType)
        : string * (string * string * string) option * (decimal * decimal * string) option =
        match cargoType with
        | General -> "GENERAL", None, None
        | Hazardous d ->
            "HAZARDOUS",
            Some(
                HazardousDeclaration.hazardClass d,
                HazardousDeclaration.unNumber d,
                HazardousDeclaration.properShippingName d
            ),
            None
        | Refrigerated t ->
            let unitStr =
                match TemperatureRequirement.unit t with
                | Celsius -> "CELSIUS"
                | Fahrenheit -> "FAHRENHEIT"

            "REFRIGERATED",
            None,
            Some(TemperatureRequirement.minTemperature t, TemperatureRequirement.maxTemperature t, unitStr)

    let private strParam (v: string option) =
        match v with
        | Some s -> SqlType.String s
        | None -> SqlType.Null

    let private decParam (v: decimal option) =
        match v with
        | Some d -> SqlType.Decimal d
        | None -> SqlType.Null

    /// DB から読み出した生レコード（復元前）。
    type private CargoRow =
        { BookingId: string
          ShipperId: string
          CargoType: string
          Weight: decimal
          Origin: string
          Destination: string
          ArrivalDeadline: string
          BookingStatus: string
          HazardClass: string option
          UnNumber: string option
          ProperShippingName: string option
          MinTemperature: decimal option
          MaxTemperature: decimal option
          TemperatureUnit: string option
          ConsigneeName: string option
          ConsigneeAddress: string option
          ConsigneeEmail: string option }

    /// 温度単位文字列を DU へ復元する。
    let private toTemperatureUnit (value: string) : Result<TemperatureUnit, DomainError> =
        match value.ToUpperInvariant() with
        | "CELSIUS" -> Ok Celsius
        | "FAHRENHEIT" -> Ok Fahrenheit
        | other -> Error(ValidationError("TemperatureUnit", sprintf "未知の温度単位です: %s" other))

    /// 生レコードから Cargo 集約を復元する（永続化データは信頼するが、値検証は通す）。
    let private reconstruct (row: CargoRow) : Result<Cargo, DomainError> =
        let toLocation field code =
            Location.create code |> Result.mapError (fun m -> ValidationError(field, m))

        result {
            let! shipperId = ShipperId.ofString row.ShipperId
            let! origin = toLocation "Origin" row.Origin
            let! destination = toLocation "Destination" row.Destination
            let deadline = DateOnly.Parse row.ArrivalDeadline
            let! routeSpec = RouteSpecification.create origin destination deadline
            let! weight = Weight.create row.Weight
            let! state = BookingState.ofString row.BookingStatus

            let! cargoType =
                match row.CargoType with
                | "GENERAL" -> Ok General
                | "HAZARDOUS" ->
                    HazardousDeclaration.create
                        (defaultArg row.HazardClass "")
                        (defaultArg row.UnNumber "")
                        (defaultArg row.ProperShippingName "")
                    |> Result.map Hazardous
                | "REFRIGERATED" ->
                    result {
                        let! unit = toTemperatureUnit (defaultArg row.TemperatureUnit "")

                        let! req =
                            TemperatureRequirement.create
                                (defaultArg row.MinTemperature 0m)
                                (defaultArg row.MaxTemperature 0m)
                                unit

                        return Refrigerated req
                    }
                | other -> Error(ValidationError("CargoType", sprintf "未知の貨物種別です: %s" other))

            let! consignee =
                match row.ConsigneeName with
                | Some name ->
                    Consignee.create name (defaultArg row.ConsigneeAddress "") (defaultArg row.ConsigneeEmail "")
                    |> Result.map Some
                | None -> Ok None

            return
                { BookingId = BookingId.ofString row.BookingId
                  ShipperId = shipperId
                  Consignee = consignee
                  RouteSpecification = routeSpec
                  CargoType = cargoType
                  Weight = weight
                  State = state
                  Dimensions = None
                  Quantity = None
                  Description = None }
        }

    /// Donald の出力ポート実装を生成する。clock は監査カラムに使う（ADR-0006）。
    let create (conn: IDbConnection) (clock: Clock) : CargoRepository =

        let save (cargo: Cargo) : Async<Result<unit, DomainError>> =
            async {
                // 予約は将来的に付随テーブル（leg 等）への複数書き込みを含むため、
                // 単一トランザクションで原子化し集約の部分永続化を防ぐ（ADR-0001 / IT1 Try#2）。
                use tx = conn.BeginTransaction()

                try
                    let now = clock ()
                    let cargoTypeStr, haz, temp = explodeCargoType cargo.CargoType
                    let hazClass = haz |> Option.map (fun (c, _, _) -> c)
                    let unNumber = haz |> Option.map (fun (_, u, _) -> u)
                    let properName = haz |> Option.map (fun (_, _, p) -> p)
                    let minTemp = temp |> Option.map (fun (mn, _, _) -> mn)
                    let maxTemp = temp |> Option.map (fun (_, mx, _) -> mx)
                    let tempUnit = temp |> Option.map (fun (_, _, u) -> u)
                    let consigneeName = cargo.Consignee |> Option.map Consignee.name
                    let consigneeAddress = cargo.Consignee |> Option.map Consignee.address
                    let consigneeEmail = cargo.Consignee |> Option.map Consignee.contactEmail

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO cargo
                            (booking_id, shipper_id, cargo_type, weight,
                             origin_unlocode, destination_unlocode, arrival_deadline, booking_status,
                             hazardous_class, un_number, proper_shipping_name,
                             min_temperature, max_temperature, temperature_unit,
                             consignee_name, consignee_address, consignee_email,
                             created_at, updated_at, version)
                        VALUES
                            (@booking_id, @shipper_id, @cargo_type, @weight,
                             @origin, @destination, @arrival_deadline, @booking_status,
                             @hazardous_class, @un_number, @proper_shipping_name,
                             @min_temperature, @max_temperature, @temperature_unit,
                             @consignee_name, @consignee_address, @consignee_email,
                             @now, @now, 0)
                        """
                    |> Db.setTransaction tx
                    |> Db.setParams
                        [ "booking_id", SqlType.String(BookingId.value cargo.BookingId)
                          "shipper_id", SqlType.String((ShipperId.value cargo.ShipperId).ToString("D"))
                          "cargo_type", SqlType.String cargoTypeStr
                          "weight", SqlType.Decimal(Weight.value cargo.Weight)
                          "origin", SqlType.String(Location.value (RouteSpecification.origin cargo.RouteSpecification))
                          "destination",
                          SqlType.String(Location.value (RouteSpecification.destination cargo.RouteSpecification))
                          "arrival_deadline",
                          SqlType.String(
                              (RouteSpecification.arrivalDeadline cargo.RouteSpecification).ToString("yyyy-MM-dd")
                          )
                          "booking_status", SqlType.String(BookingState.toString cargo.State)
                          "hazardous_class", strParam hazClass
                          "un_number", strParam unNumber
                          "proper_shipping_name", strParam properName
                          "min_temperature", decParam minTemp
                          "max_temperature", decParam maxTemp
                          "temperature_unit", strParam tempUnit
                          "consignee_name", strParam consigneeName
                          "consignee_address", strParam consigneeAddress
                          "consignee_email", strParam consigneeEmail
                          "now", SqlType.String(now.UtcDateTime.ToString("o")) ]
                    |> Db.setTransaction tx
                    |> Db.exec

                    tx.Commit()
                    return Ok()
                with ex ->
                    tx.Rollback()
                    return Error(BusinessRuleViolation("CargoRepository", ex.Message))
            }

        let update (cargo: Cargo) : Async<Result<unit, DomainError>> =
            async {
                // 状態遷移の更新。IT2 は booking_status と version を更新する（付随テーブルは後続 IT）。
                use tx = conn.BeginTransaction()

                try
                    let now = clock ()
                    let bookingIdStr = BookingId.value cargo.BookingId

                    // 更新対象の存在を確認する（存在しない予約への更新を silent 成功にしない）。
                    let existing =
                        conn
                        |> Db.newCommand "SELECT COUNT(*) AS cnt FROM cargo WHERE booking_id = @booking_id"
                        |> Db.setTransaction tx
                        |> Db.setParams [ "booking_id", SqlType.String bookingIdStr ]
                        |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")
                        |> Option.defaultValue 0

                    if existing = 0 then
                        tx.Rollback()
                        return Error(NotFound("Cargo", bookingIdStr))
                    else
                        conn
                        |> Db.newCommand
                            """
                            UPDATE cargo
                            SET booking_status = @booking_status, updated_at = @now, version = version + 1
                            WHERE booking_id = @booking_id
                            """
                        |> Db.setTransaction tx
                        |> Db.setParams
                            [ "booking_status", SqlType.String(BookingState.toString cargo.State)
                              "now", SqlType.String(now.UtcDateTime.ToString("o"))
                              "booking_id", SqlType.String bookingIdStr ]
                        |> Db.exec

                        tx.Commit()
                        return Ok()
                with ex ->
                    tx.Rollback()
                    return Error(BusinessRuleViolation("CargoRepository", ex.Message))
            }

        let findById (bookingId: BookingId) : Async<Result<Cargo option, DomainError>> =
            async {
                try
                    let row =
                        conn
                        |> Db.newCommand
                            """
                            SELECT booking_id, shipper_id, cargo_type, weight,
                                   origin_unlocode, destination_unlocode, arrival_deadline, booking_status,
                                   hazardous_class, un_number, proper_shipping_name,
                                   min_temperature, max_temperature, temperature_unit,
                                   consignee_name, consignee_address, consignee_email
                            FROM cargo WHERE booking_id = @booking_id
                            """
                        |> Db.setParams [ "booking_id", SqlType.String(BookingId.value bookingId) ]
                        |> Db.querySingle (fun rd ->
                            { BookingId = rd.ReadString "booking_id"
                              ShipperId = rd.ReadString "shipper_id"
                              CargoType = rd.ReadString "cargo_type"
                              Weight = rd.ReadDecimal "weight"
                              Origin = rd.ReadString "origin_unlocode"
                              Destination = rd.ReadString "destination_unlocode"
                              ArrivalDeadline = rd.ReadString "arrival_deadline"
                              BookingStatus = rd.ReadString "booking_status"
                              HazardClass = rd.ReadStringOption "hazardous_class"
                              UnNumber = rd.ReadStringOption "un_number"
                              ProperShippingName = rd.ReadStringOption "proper_shipping_name"
                              MinTemperature = rd.ReadDecimalOption "min_temperature"
                              MaxTemperature = rd.ReadDecimalOption "max_temperature"
                              TemperatureUnit = rd.ReadStringOption "temperature_unit"
                              ConsigneeName = rd.ReadStringOption "consignee_name"
                              ConsigneeAddress = rd.ReadStringOption "consignee_address"
                              ConsigneeEmail = rd.ReadStringOption "consignee_email" })

                    match row with
                    | Some r -> return reconstruct r |> Result.map Some
                    | None -> return Ok None
                with ex ->
                    return Error(BusinessRuleViolation("CargoRepository", ex.Message))
            }

        { Save = save
          Update = update
          FindById = findById }
