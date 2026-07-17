namespace CargoTracker.Shipper.Infrastructure

open System
open System.Data
open Donald
open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain
open CargoTracker.Shipper.Application

// Shipper コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// ANSI 標準の範囲で SQL を記述し、SQLite / PostgreSQL 両方言で動作させる（ADR-0003）。

/// 一覧表示用の読み取りモデル（CQRS の Read 側・DTO 直接射影）。
type ShipperListItem =
    { Code: string
      Name: string
      Email: string
      ShipperType: string
      DiscountRate: decimal }

/// 荷主選択用の軽量読み取りモデル（貨物予約フォームの荷主ドロップダウン・ADR-0008）。
type ShipperOption =
    { Uuid: string
      Code: string
      Name: string }

module ShipperQueries =

    /// 荷主選択肢を取得する（コード順・shipper_uuid を持つ荷主のみ）。
    let findAllForSelection (conn: IDbConnection) : ShipperOption list =
        conn
        |> Db.newCommand
            "SELECT shipper_uuid, shipper_code, name FROM shipper WHERE shipper_uuid IS NOT NULL ORDER BY shipper_code"
        |> Db.query (fun rd ->
            { Uuid = rd.ReadString "shipper_uuid"
              Code = rd.ReadString "shipper_code"
              Name = rd.ReadString "name" })

    /// 荷主 UUID から法人かどうかを判定する（US22 法人割引の合成層向け）。存在しなければ None。
    let isCorporateByUuid (conn: IDbConnection) (shipperUuid: string) : bool option =
        conn
        |> Db.newCommand "SELECT shipper_type FROM shipper WHERE shipper_uuid = @uuid"
        |> Db.setParams [ "uuid", SqlType.String shipperUuid ]
        |> Db.querySingle (fun rd -> rd.ReadString "shipper_type")
        |> Option.map (fun t -> t = "CORPORATE")

    /// 荷主 UUID からメールアドレスを解決する（通知の連絡先解決・合成層向け・US23/IT8）。
    let findEmailByUuid (conn: IDbConnection) (shipperUuid: string) : string option =
        conn
        |> Db.newCommand "SELECT email FROM shipper WHERE shipper_uuid = @uuid"
        |> Db.setParams [ "uuid", SqlType.String shipperUuid ]
        |> Db.querySingle (fun rd -> rd.ReadString "email")

    /// 予約 ID から荷主メールアドレスを解決する（cargo.shipper_id = shipper.shipper_uuid 経由・通知の連絡先解決）。
    let findEmailByBooking (conn: IDbConnection) (bookingId: string) : string option =
        conn
        |> Db.newCommand
            """
            SELECT s.email
            FROM cargo c
            JOIN shipper s ON s.shipper_uuid = c.shipper_id
            WHERE c.booking_id = @bid
            """
        |> Db.setParams [ "bid", SqlType.String bookingId ]
        |> Db.querySingle (fun rd -> rd.ReadString "email")

    /// 荷主一覧を取得する（コード順）。
    let findAll (conn: IDbConnection) : ShipperListItem list =
        conn
        |> Db.newCommand
            "SELECT shipper_code, name, email, shipper_type, discount_rate FROM shipper ORDER BY shipper_code"
        |> Db.query (fun rd ->
            { Code = rd.ReadString "shipper_code"
              Name = rd.ReadString "name"
              Email = rd.ReadString "email"
              ShipperType = rd.ReadString "shipper_type"
              DiscountRate = rd.ReadDecimal "discount_rate" })

module ShipperRepository =

    /// ShipperKind を永続化用の (種別文字列, 契約番号 option, 割引率) に展開する。
    let private explodeKind (kind: ShipperKind) : string * string option * decimal =
        match kind with
        | Individual -> "INDIVIDUAL", None, 0.0000m
        | Corporate(contract, rate) -> "CORPORATE", Some(ContractNumber.value contract), DiscountRate.value rate

    /// Donald の出力ポート実装を生成する。clock は監査カラムのタイムスタンプに使う（ADR-0006）。
    let create (conn: IDbConnection) (clock: Clock) : ShipperRepository =

        let existsByEmail (email: Email) : Async<Result<bool, DomainError>> =
            async {
                try
                    let count =
                        conn
                        |> Db.newCommand "SELECT COUNT(*) AS cnt FROM shipper WHERE email = @email"
                        |> Db.setParams [ "email", SqlType.String(Email.value email) ]
                        |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")

                    return Ok(count |> Option.defaultValue 0 > 0)
                with ex ->
                    return Error(BusinessRuleViolation("ShipperRepository", ex.Message))
            }

        let save (shipper: Shipper) : Async<Result<unit, DomainError>> =
            async {
                try
                    let kindStr, contract, rate = explodeKind shipper.Kind
                    let now = clock ()

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO shipper
                            (shipper_code, shipper_uuid, shipper_type, name, email, phone,
                             contract_number, discount_rate, created_at, updated_at, version)
                        VALUES
                            (@shipper_code, @shipper_uuid, @shipper_type, @name, @email, @phone,
                             @contract_number, @discount_rate, @now, @now, 0)
                        """
                    |> Db.setParams
                        [ "shipper_code", SqlType.String(ShipperCode.value shipper.Code)
                          // ShipperId（Guid）を業務識別子として永続化する（ADR-0008）。
                          "shipper_uuid", SqlType.String((ShipperId.value shipper.Id).ToString("D"))
                          "shipper_type", SqlType.String kindStr
                          "name", SqlType.String(ShipperName.value shipper.Name)
                          "email", SqlType.String(Email.value shipper.Email)
                          "phone",
                          (match shipper.Phone with
                           | Some p -> SqlType.String(Phone.value p)
                           | None -> SqlType.Null)
                          "contract_number",
                          (match contract with
                           | Some c -> SqlType.String c
                           | None -> SqlType.Null)
                          "discount_rate", SqlType.Decimal rate
                          "now", SqlType.String(now.UtcDateTime.ToString("o")) ]
                    |> Db.exec

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("ShipperRepository", ex.Message))
            }

        { ExistsByEmail = existsByEmail
          Save = save }
